#!/usr/bin/env bash
# Установка и обновление wg-warden на VPS: панель открывается по IP, без домена.
# Запускать на Linux-VPS из папки проекта: ./deploy/vps-install.sh
set -euo pipefail

PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$PROJECT_DIR"

ENV_FILE=".env.vps"

say() { printf '\n==> %s\n' "$*"; }
die() { printf '\nОшибка: %s\n' "$*" >&2; exit 1; }

as_root() {
  if [ "$(id -u)" -eq 0 ]; then "$@"; else sudo "$@"; fi
}

compose() {
  docker compose -f docker-compose.vps.yml --env-file "$ENV_FILE" "$@"
}

[ "$(uname -s)" = "Linux" ] || die "скрипт предназначен для Linux-VPS. Для локального запуска используйте ./install.sh"
command -v curl >/dev/null 2>&1 || die "нужен curl"

if ! command -v docker >/dev/null 2>&1; then
  if [ "$(id -u)" -ne 0 ] && ! command -v sudo >/dev/null 2>&1; then
    die "для установки Docker нужен root или sudo"
  fi
  say "Docker не найден — устанавливаю Docker Engine"
  curl -fsSL https://get.docker.com | as_root sh
  as_root systemctl enable --now docker
  if [ "$(id -u)" -ne 0 ]; then
    as_root usermod -aG docker "$(id -un)"
    die "Docker установлен. Перезайдите по SSH (чтобы применилась группа docker) и запустите скрипт снова"
  fi
fi

if ! docker info >/dev/null 2>&1; then
  if [ "$(id -u)" -eq 0 ]; then
    systemctl start docker 2>/dev/null || true
  fi
  docker info >/dev/null 2>&1 || die "Docker недоступен. Добавьте пользователя $(id -un) в группу docker и перезайдите по SSH: sudo usermod -aG docker $(id -un)"
fi
docker compose version >/dev/null 2>&1 || die "нужен Docker Compose v2 (плагин 'docker compose')"

# На VPS с 1 ГБ RAM сборка образов Maven + Node без swap падает по OOM.
mem_kb="$(awk '/^MemTotal:/{print $2}' /proc/meminfo)"
swap_kb="$(awk '/^SwapTotal:/{print $2}' /proc/meminfo)"
if [ "${mem_kb:-0}" -lt 2097152 ] && [ "${swap_kb:-0}" -lt 1048576 ]; then
  if [ "$(id -u)" -eq 0 ] || command -v sudo >/dev/null 2>&1; then
    say "RAM ${mem_kb} КБ: создаю swap-файл 2 ГБ для сборки образов"
    as_root fallocate -l 2G /swapfile 2>/dev/null || as_root dd if=/dev/zero of=/swapfile bs=1M count=2048
    as_root chmod 600 /swapfile
    as_root mkswap /swapfile >/dev/null
    as_root swapon /swapfile
    grep -q '^/swapfile ' /etc/fstab 2>/dev/null || echo '/swapfile none swap sw 0 0' | as_root tee -a /etc/fstab >/dev/null
  else
    say "Внимание: мало RAM и нет swap. Сборка образов может упасть по памяти."
  fi
fi

if [ ! -f "$ENV_FILE" ]; then
  command -v openssl >/dev/null 2>&1 || die "нужен openssl для генерации секретов"
  cp .env.vps.example "$ENV_FILE"
  sed -i.bak \
    -e "s|^DB_PASSWORD=.*|DB_PASSWORD=$(openssl rand -hex 24)|" \
    -e "s|^APP_MASTER_KEY=.*|APP_MASTER_KEY=$(openssl rand -base64 32 | tr -d '\n')|" \
    -e "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')|" \
    "$ENV_FILE"
  rm -f "$ENV_FILE.bak"
  chmod 600 "$ENV_FILE"
  say "Создан $ENV_FILE с новыми секретами"
fi

for key in DB_PASSWORD APP_MASTER_KEY JWT_SECRET; do
  grep -q "^${key}=..*" "$ENV_FILE" || die "$key не заполнен в $ENV_FILE"
done

web_port="$(sed -n 's/^WEB_PORT=//p' "$ENV_FILE" | tail -n 1)"
web_port="${web_port:-80}"

# Собираем последовательно: на слабом VPS параллельная сборка Maven и Node упирается в память.
say "Собираю образы (на VPS с 1 ГБ RAM это может занять 5–15 минут)"
compose build app
compose build web

say "Запускаю контейнеры"
compose up -d --remove-orphans

say "Ожидаю готовности приложения"
healthy=0
for _ in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:${web_port}/actuator/health" >/dev/null 2>&1; then
    healthy=1
    break
  fi
  sleep 3
done

if [ "$healthy" -ne 1 ]; then
  docker compose -f docker-compose.vps.yml --env-file "$ENV_FILE" ps >&2 || true
  die "приложение не ответило за 3 минуты. Логи: ./deploy/vps-service.sh logs"
fi

public_ip="$(curl -fsS --max-time 5 https://api.ipify.org 2>/dev/null || true)"
if [ -z "$public_ip" ]; then
  public_ip="$(hostname -I 2>/dev/null | awk '{print $1}')"
fi

address="http://${public_ip:-<IP-VPS>}"
[ "$web_port" = "80" ] || address="${address}:${web_port}"

printf '\nwg-warden запущен: %s\n' "$address"
printf 'Управление: ./deploy/vps-service.sh {start|stop|restart|status|logs}\n'

if grep -q '^APP_REGISTRATION_ENABLED=true' "$ENV_FILE"; then
  cat <<'HINT'

Регистрация пока открыта. Сразу после установки:
  1. откройте панель по адресу выше и создайте свой аккаунт;
  2. поставьте в .env.vps строку APP_REGISTRATION_ENABLED=false;
  3. примените настройку: ./deploy/vps-service.sh restart

Иначе любой, кто найдёт IP, сможет зарегистрироваться в вашей панели.
HINT
fi

cat <<'HINT'

Доступ по IP идёт по HTTP, без TLS: логин и токен передаются открыто.
Для постоянной работы выделите домен и поставьте перед панелью HTTPS-прокси.
HINT
