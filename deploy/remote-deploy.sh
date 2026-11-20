#!/usr/bin/env bash
# Spec 039, ADR-0037: the part of the deployment of the API that runs on the VPS, as the user `deploy`. Started by the
# workflow Deploy (.github/workflows/deploy.yml) over SSH, after it has copied the bundle of the commit:
#
#   <bundle>/files/...   the files of deploy/bundle.txt at their paths, and the .env and deploy/db/.env it rendered
#
#   bash <bundle>/files/deploy/remote-deploy.sh <commit> <app dir> <bundle> <registry user>   (the registry token on stdin)
#
# Every check comes before any change. The order: checks, pull of the new image, labeled dump, files in place, the API
# recreated, its health awaited. The database stack is never restarted. Lines starting with `@@summary@@` are the
# run's summary (names of variables, never their values). DEPLOY_REHEARSAL=1 skips the registry (login and pull) to
# rehearse with an image that is already local.
set -euo pipefail

COMMIT=${1:?commit}
APP_DIR=${2:?app dir}
BUNDLE=${3:?bundle dir}
REGISTRY_USER=${4:-}
FILES="$BUNDLE/files"
SHORT=${COMMIT:0:7}
CONTAINER=bowspleef-ranked-api
INFO_URL=http://localhost:8085/actuator/info
HEALTH_LIMIT=${HEALTH_LIMIT:-180}

summary() { printf '@@summary@@%s\n' "$*"; }
fail() { echo "::error::$*" >&2; exit 1; }

# The value of KEY in an env file, without the quotes around it (the hand-written files and the rendered ones)
env_value() {
  local file=$1 key=$2 line
  line=$(grep -E "^[[:space:]]*${key}=" "$file" 2>/dev/null | tail -n 1) || true
  line=${line#*=}
  line=${line%$'\r'}
  if [[ $line == \'*\' || $line == \"*\" ]]; then line=${line:1:${#line}-2}; fi
  printf '%s' "$line"
}

# The names whose value differs between two env files (or that one of them lacks)
changed_names() {
  local old=$1 new=$2 key
  { grep -oE '^[[:space:]]*[A-Z][A-Z0-9_]*=' "$old" 2>/dev/null; grep -oE '^[A-Z][A-Z0-9_]*=' "$new"; } \
    | tr -d ' =' | sort -u | while read -r key; do
      if ! grep -qE "^[[:space:]]*${key}=" "$old" 2>/dev/null || ! grep -qE "^${key}=" "$new" \
         || [ "$(env_value "$old" "$key")" != "$(env_value "$new" "$key")" ]; then
        printf '%s ' "$key"
      fi
    done
}

schema_version() {
  curl -fsS --max-time 5 "$INFO_URL" 2>/dev/null | grep -oE '"schemaVersion":[0-9]+' | cut -d: -f2 || true
}

cd "$APP_DIR"
if [ ! -f "$FILES/.env" ] || [ ! -f "$FILES/deploy/db/.env" ]; then fail "The bundle has no rendered .env files"; fi
command -v curl >/dev/null || fail "curl is needed on the VPS"

# 1. Checks -----------------------------------------------------------------------------------------------------------
# REQ-039-07: the user and password of an initialized database do not change by a deployment
if [ -f deploy/db/.env ]; then
  for key in POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD; do
    if [ "$(env_value deploy/db/.env "$key")" != "$(env_value "$FILES/deploy/db/.env" "$key")" ]; then
      fail "$key differs from the VPS's deploy/db/.env: changing the database's settings is a manual procedure (docs/operations/deployment.md), not a deployment. Nothing was changed."
    fi
  done
fi
NEW_IMAGE=$(env_value "$FILES/.env" API_IMAGE)
[ -n "$NEW_IMAGE" ] || fail "The rendered .env has no API_IMAGE"
OLD_IMAGE=$(docker inspect --format '{{.Config.Image}}' "$CONTAINER" 2>/dev/null || echo none)
SCHEMA_BEFORE=$(schema_version)

# 2. The image ----------------------------------------------------------------------------------------------------------
if [ "${DEPLOY_REHEARSAL:-}" != 1 ]; then
  IFS= read -r TOKEN || fail "No registry token on stdin"
  trap 'docker logout ghcr.io >/dev/null 2>&1 || true' EXIT
  printf '%s' "$TOKEN" | docker login ghcr.io -u "$REGISTRY_USER" --password-stdin >/dev/null \
    || fail "docker login ghcr.io failed. Nothing was changed."
  unset TOKEN
  # API_IMAGE from the shell wins over the current .env, so the new image is pulled before any file changes
  API_IMAGE=$NEW_IMAGE docker compose pull --quiet api || fail "The pull of $NEW_IMAGE failed. Nothing was changed."
fi

# 3. The dump (REQ-039-06) ----------------------------------------------------------------------------------------------
DUMP_OUTPUT=$(./deploy/db/backup.sh "before-$SHORT" 2>&1) || { echo "$DUMP_OUTPUT" >&2; fail "The dump failed. Nothing was changed."; }
echo "$DUMP_OUTPUT"

# 4. The files ------------------------------------------------------------------------------------------------------
ENV_CHANGED=$(changed_names .env "$FILES/.env")
DB_ENV_CHANGED=$(changed_names deploy/db/.env "$FILES/deploy/db/.env")
for env_file in .env deploy/db/.env; do
  [ -f "$env_file" ] && cp -p "$env_file" "$env_file.bak"
done
(cd "$FILES" && find . -type f -print0) | while IFS= read -r -d '' f; do
  mkdir -p "$(dirname "$f")"
  cp "$FILES/$f" "$f"
done
chmod 600 .env deploy/db/.env .env.bak deploy/db/.env.bak 2>/dev/null || true
chmod +x deploy/db/*.sh deploy/remote-deploy.sh

# 5. The API ----------------------------------------------------------------------------------------------------------
docker compose up -d --no-build api
STATUS=starting
for _ in $(seq 1 $((HEALTH_LIMIT / 5))); do
  STATUS=$(docker inspect --format '{{.State.Health.Status}}' "$CONTAINER" 2>/dev/null || echo missing)
  [ "$STATUS" = healthy ] && break
  sleep 5
done
SCHEMA_AFTER=$(schema_version)

summary "| | |"
summary "|---|---|"
summary "| Image | \`$NEW_IMAGE\` (was \`$OLD_IMAGE\`) |"
summary "| Dump | \`$(printf '%s' "$DUMP_OUTPUT" | grep -oE '[^ /]+\.dump' | tail -n 1)\` |"
summary "| Schema version | ${SCHEMA_BEFORE:-unknown} → ${SCHEMA_AFTER:-unknown} |"
summary "| Changed in \`.env\` | ${ENV_CHANGED:-none} |"
summary "| Changed in \`deploy/db/.env\` | ${DB_ENV_CHANGED:-none} |"
summary "| Health | $STATUS |"
if [ "$STATUS" != healthy ]; then
  docker compose logs --no-color --tail 80 api >&2 || true
  fail "The API is $STATUS after ${HEALTH_LIMIT} s (log above). It was not rolled back: deploy the previous commit, and restore the dump before-$SHORT only if a migration broke the data."
fi
rm -rf "$BUNDLE"
