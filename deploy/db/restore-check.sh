#!/bin/sh
# Proof that the newest dump can be restored (ADR-0018). A backup that was never restored is not a backup.
#
#   deploy/db/restore-check.sh [dump file]
#
# Restores the dump (default: the newest of BACKUP_DIR) into a throwaway PostgreSQL container with no published port,
# prints the row counts of the main tables, and removes the container. Never touches the production database.
# Run it once a month and after any change to the backup.
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
ENV_FILE=${ENV_FILE:-$HERE/.env}
if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi

BACKUP_DIR=${BACKUP_DIR:-$HOME/ranked/backups}
DUMP=${1:-$(ls -1 "$BACKUP_DIR"/bowspleef-ranked-*.dump 2> /dev/null | sort | tail -n 1)}
[ -n "$DUMP" ] && [ -s "$DUMP" ] || { echo "No dump found (BACKUP_DIR=$BACKUP_DIR)" >&2; exit 1; }

NAME="ranked-restore-check-$$"
cleanup() { docker rm -f "$NAME" > /dev/null 2>&1 || true; }
trap cleanup EXIT INT TERM

echo "Restoring $(basename "$DUMP") into a throwaway container…"
docker run -d --name "$NAME" -e POSTGRES_USER=check -e POSTGRES_PASSWORD=check -e POSTGRES_DB=check postgres:16-alpine > /dev/null

# The image first starts a temporary server (socket only) to create the database, stops it, then starts the real one.
# pg_isready on the socket answers during the first phase, and the restore would hit a server that is about to stop.
# Only the real server listens on TCP: every call below goes through 127.0.0.1 (trusted inside the container).
ready=0
for _ in $(seq 1 60); do
  if docker exec "$NAME" pg_isready -h 127.0.0.1 -U check -d check > /dev/null 2>&1; then ready=1; break; fi
  sleep 1
done
[ "$ready" = 1 ] || { echo "The throwaway database did not start" >&2; exit 1; }

# Warnings such as "transaction_timeout" (dump from a newer server) are harmless: the counts decide
docker exec -i "$NAME" pg_restore -h 127.0.0.1 -U check -d check --no-owner --no-privileges < "$DUMP" || echo "(pg_restore reported warnings)"

docker exec "$NAME" psql -h 127.0.0.1 -U check -d check -v ON_ERROR_STOP=1 -c "
  SELECT 'player' AS tbl, count(*) FROM player
  UNION ALL SELECT 'match', count(*) FROM \"match\"
  UNION ALL SELECT 'ranked_season', count(*) FROM ranked_season
  UNION ALL SELECT 'flyway migrations', count(*) FROM flyway_schema_history;" || { echo "Restore check FAILED" >&2; exit 1; }
echo "Restore check ok."
