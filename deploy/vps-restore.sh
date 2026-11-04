#!/usr/bin/env bash
# Восстановление базы wg-warden из дампа ./deploy/vps-backup.sh.
# Запуск: ./deploy/vps-restore.sh backups/wg-warden-20260101-120000.sql.gz
set -euo pipefail

PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$PROJECT_DIR"

ENV_FILE=".env.vps"
dump="${1:-}"

say() { printf '==> %s\n' "$*"; }
die() { printf 'Ошибка: %s\n' "$*" >&2; exit 1; }

[ -n "$dump" ] || die "укажите файл дампа: ./deploy/vps-restore.sh backups/wg-warden-<дата>.sql.gz"
[ -f "$dump" ] || die "файл не найден: $dump"
[ -f "$ENV_FILE" ] || die "не найден $ENV_FILE"

env_value() {
  sed -n "s|^$1=||p" "$ENV_FILE" | tail -n 1
}

db_user="$(env_value DB_USER)"
db_user="${db_user:-wgwarden}"
app_domain="$(env_value APP_DOMAIN)"
app_domain="${app_domain// /}"
if [ -n "$app_domain" ]; then
  COMPOSE_FILE="docker-compose.tls.yml"
else
  COMPOSE_FILE="docker-compose.vps.yml"
fi

compose() {
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
}

cat <<WARN
Восстановление заменит текущие данные в базе содержимым $dump.
Серверы, peers, пользователи и метрики, созданные после этого дампа, будут потеряны.
Секреты в дампе расшифровываются только тем APP_MASTER_KEY, с которым он снят:
если .env.vps менялся, восстановите и его (копия лежит рядом с дампом — env-<дата>.vps).
WARN

if [ -t 0 ]; then
  printf 'Продолжить? Введите yes: '
  read -r answer
  [ "$answer" = "yes" ] || die "отменено"
else
  die "восстановление требует подтверждения в терминале (запустите команду вручную)"
fi

say "Останавливаю панель"
compose stop web app

compose ps --status running --services 2>/dev/null | grep -qx db || die "контейнер базы не запущен: ./deploy/vps-service.sh start"

say "Загружаю дамп"
gunzip -c "$dump" | compose exec -T db psql -U "$db_user" -d wgwarden -v ON_ERROR_STOP=1 >/dev/null

say "Запускаю панель"
compose up -d

printf '\nБаза восстановлена из %s\n' "$dump"
