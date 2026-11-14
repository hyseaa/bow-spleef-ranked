#!/bin/sh
# Dump of the production database (ADR-0018).
#
#   deploy/db/backup.sh [label]
#
# Writes BACKUP_DIR/bowspleef-ranked-<YYYYmmdd-HHMM>[-<label>].dump (custom format, `pg_dump -Fc`), checks that it is
# readable, keeps the newest KEEP dumps without a label (a dump with a label, e.g. `before-V29`, is never deleted
# here), then runs OFFSITE_COMMAND on it if one is set. Settings: deploy/db/.env (see .env.example).
# Cron (daily at 04:00):  0 4 * * * /root/ranked/bow-spleef-ranked/deploy/db/backup.sh >> /root/ranked/backups/backup.log 2>&1
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
ENV_FILE=${ENV_FILE:-$HERE/.env}
if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi

: "${POSTGRES_DB:?POSTGRES_DB is not set (deploy/db/.env)}"
: "${POSTGRES_USER:?POSTGRES_USER is not set (deploy/db/.env)}"
CONTAINER=${DB_CONTAINER:-bowspleef-ranked-db}
BACKUP_DIR=${BACKUP_DIR:-$HOME/ranked/backups}
KEEP=${KEEP:-21}
LABEL=${1:-}

case "$KEEP" in ''|*[!0-9]*) echo "KEEP must be a number, got '$KEEP'" >&2; exit 1 ;; esac
case "$LABEL" in *[!A-Za-z0-9_-]*) echo "The label may only contain letters, digits, - and _" >&2; exit 1 ;; esac

umask 077   # the dump holds personal data (Discord ids, Minecraft UUIDs)
mkdir -p "$BACKUP_DIR"

stamp=$(date +%Y%m%d-%H%M)
name="bowspleef-ranked-$stamp${LABEL:+-$LABEL}.dump"
final="$BACKUP_DIR/$name"
part="$final.part"

fail() {
  rm -f "$part"
  echo "$(date '+%F %T') backup FAILED: $1" >&2
  exit 1
}

[ ! -e "$final" ] || fail "$name already exists (one dump per minute and label)"

docker exec "$CONTAINER" pg_dump -Fc -U "$POSTGRES_USER" "$POSTGRES_DB" > "$part" || fail "pg_dump failed"
[ -s "$part" ] || fail "the dump is empty"
docker exec -i "$CONTAINER" pg_restore -l < "$part" > /dev/null || fail "pg_restore cannot read the dump"
mv "$part" "$final"
echo "$(date '+%F %T') backup ok: $name ($(wc -c < "$final" | tr -d ' ') bytes)"

# Retention: unlabeled dumps only (bowspleef-ranked-<8 digits>-<4 digits>.dump), newest KEEP kept
ls -1 "$BACKUP_DIR" | grep -E '^bowspleef-ranked-[0-9]{8}-[0-9]{4}\.dump$' | sort -r | tail -n +"$((KEEP + 1))" |
  while IFS= read -r old; do
    rm -f -- "$BACKUP_DIR/$old"
    echo "$(date '+%F %T') removed old dump: $old"
  done

if [ -n "${OFFSITE_COMMAND:-}" ]; then
  # the command sees the dump path as "$1"
  sh -c "$OFFSITE_COMMAND" offsite "$final" || { echo "$(date '+%F %T') OFFSITE_COMMAND FAILED (the local dump is kept)" >&2; exit 2; }
  echo "$(date '+%F %T') offsite command ok"
fi
