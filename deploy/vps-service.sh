#!/usr/bin/env bash
# Управление VPS-запуском wg-warden.
set -euo pipefail

PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$PROJECT_DIR"

if [ ! -f .env.vps ]; then
  echo "Не найден .env.vps. Сначала выполните ./deploy/vps-install.sh"
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker недоступен. Запустите Docker Engine и повторите команду."
  exit 1
fi

env_value() {
  sed -n "s|^$1=||p" .env.vps | tail -n 1
}

app_domain="$(env_value APP_DOMAIN)"
app_domain="${app_domain// /}"

if [ -n "$app_domain" ]; then
  COMPOSE_FILE="docker-compose.tls.yml"
  https_port="$(env_value HTTPS_PORT)"
  https_port="${https_port:-443}"
  address="https://${app_domain}"
  [ "$https_port" = "443" ] || address="${address}:${https_port}"
else
  COMPOSE_FILE="docker-compose.vps.yml"
  web_port="$(env_value WEB_PORT)"
  web_port="${web_port:-80}"
  address="http://$(curl -fsS --max-time 5 https://api.ipify.org 2>/dev/null || hostname -I 2>/dev/null | awk '{print $1}')"
  [ "$web_port" = "80" ] || address="${address}:${web_port}"
fi

compose() {
  docker compose -f "$COMPOSE_FILE" --env-file .env.vps "$@"
}

case "${1:-}" in
  start)
    compose up -d
    echo "wg-warden включён: ${address}"
    ;;
  stop)
    compose stop
    echo "wg-warden выключен. Данные сохранены."
    ;;
  restart)
    # up -d пересоздаёт контейнер, если изменился .env.vps
    compose up -d
    echo "wg-warden применён: ${address}"
    ;;
  status)
    compose ps
    ;;
  logs)
    compose logs -f
    ;;
  *)
    echo "Использование: ./deploy/vps-service.sh {start|stop|restart|status|logs}"
    exit 2
    ;;
esac
