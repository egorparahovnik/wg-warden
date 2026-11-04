#!/usr/bin/env bash
# Установка и обновление wg-warden на VPS.
# Без APP_DOMAIN панель открывается по IP через HTTP, с APP_DOMAIN — по HTTPS через Caddy.
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

env_value() {
  sed -n "s|^$1=||p" "$ENV_FILE" | tail -n 1
}

compose() {
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
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
    -e "s|^APP_BOOTSTRAP_TOKEN=.*|APP_BOOTSTRAP_TOKEN=$(openssl rand -hex 24)|" \
    "$ENV_FILE"
  rm -f "$ENV_FILE.bak"
  chmod 600 "$ENV_FILE"
  say "Создан $ENV_FILE с новыми секретами"
fi

# Обновление со старой версии: добавляем setup-токен, если его ещё нет.
if ! grep -q '^APP_BOOTSTRAP_TOKEN=..*' "$ENV_FILE"; then
  if grep -q '^APP_BOOTSTRAP_TOKEN=' "$ENV_FILE"; then
    sed -i.bak "s|^APP_BOOTSTRAP_TOKEN=.*|APP_BOOTSTRAP_TOKEN=$(openssl rand -hex 24)|" "$ENV_FILE"
    rm -f "$ENV_FILE.bak"
  else
    printf '\nAPP_BOOTSTRAP_TOKEN=%s\n' "$(openssl rand -hex 24)" >> "$ENV_FILE"
  fi
  chmod 600 "$ENV_FILE"
  say "Добавлен APP_BOOTSTRAP_TOKEN для создания первого аккаунта"
fi

for key in DB_PASSWORD APP_MASTER_KEY JWT_SECRET APP_BOOTSTRAP_TOKEN; do
  grep -q "^${key}=..*" "$ENV_FILE" || die "$key не заполнен в $ENV_FILE"
done
bootstrap_token="$(env_value APP_BOOTSTRAP_TOKEN)"

web_port="$(env_value WEB_PORT)"
web_port="${web_port:-80}"
app_domain="$(env_value APP_DOMAIN)"
app_domain="${app_domain// /}"

if [ -n "$app_domain" ]; then
  COMPOSE_FILE="docker-compose.tls.yml"
  tls_mode="$(env_value TLS_MODE)"
  tls_mode="${tls_mode:-acme}"
  http_port="$(env_value HTTP_PORT)"
  http_port="${http_port:-80}"
  https_port="$(env_value HTTPS_PORT)"
  https_port="${https_port:-443}"
  case "$tls_mode" in
    acme|internal) ;;
    *) die "TLS_MODE должен быть acme или internal (сейчас: $tls_mode)" ;;
  esac
  [ -f "deploy/Caddyfile.${tls_mode}" ] || die "не найден deploy/Caddyfile.${tls_mode}"
  say "Домен ${app_domain}: панель пойдёт по HTTPS (режим сертификата: ${tls_mode})"

  if [ "$tls_mode" = "acme" ]; then
    resolved="$(getent hosts "$app_domain" 2>/dev/null | awk '{print $1}' | head -n 1 || true)"
    public_ip="$(curl -fsS --max-time 5 https://api.ipify.org 2>/dev/null || true)"
    if [ -n "$resolved" ] && [ -n "$public_ip" ] && [ "$resolved" != "$public_ip" ]; then
      say "Внимание: ${app_domain} указывает на ${resolved}, а IP сервера ${public_ip}. Let's Encrypt не выдаст сертификат, пока A-запись не исправлена."
    elif [ -z "$resolved" ]; then
      say "Внимание: ${app_domain} пока не резолвится. Сертификат не выпустится, пока не появится A-запись на IP этого VPS."
    fi
  else
    say "Самоподписанный сертификат: браузер покажет предупреждение, трафик при этом шифруется"
  fi
else
  COMPOSE_FILE="docker-compose.vps.yml"
fi

# Собираем последовательно: на слабом VPS параллельная сборка Maven и Node упирается в память.
say "Собираю образы (на VPS с 1 ГБ RAM это может занять 5–15 минут)"
compose build app
compose build web

say "Запускаю контейнеры"
compose up -d --remove-orphans

say "Ожидаю готовности приложения"
healthy=0
for _ in $(seq 1 60); do
  if [ -n "$app_domain" ]; then
    probe=(curl -fsS --resolve "${app_domain}:${https_port}:127.0.0.1")
    [ "$tls_mode" = "internal" ] && probe+=(-k)
    probe+=("https://${app_domain}:${https_port}/actuator/health")
  else
    probe=(curl -fsS "http://127.0.0.1:${web_port}/actuator/health")
  fi
  if "${probe[@]}" >/dev/null 2>&1; then
    healthy=1
    break
  fi
  sleep 3
done

if [ "$healthy" -ne 1 ]; then
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" ps >&2 || true
  if [ -n "$app_domain" ] && [ "$tls_mode" = "acme" ]; then
    die "приложение не ответило за 3 минуты. Если порт 80 закрыт или A-запись неверна, сертификат не выпустится. Логи: ./deploy/vps-service.sh logs"
  fi
  die "приложение не ответило за 3 минуты. Логи: ./deploy/vps-service.sh logs"
fi

if [ -n "$app_domain" ]; then
  address="https://${app_domain}"
  [ "$https_port" = "443" ] || address="${address}:${https_port}"
else
  public_ip="$(curl -fsS --max-time 5 https://api.ipify.org 2>/dev/null || true)"
  if [ -z "$public_ip" ]; then
    public_ip="$(hostname -I 2>/dev/null | awk '{print $1}')"
  fi
  address="http://${public_ip:-<IP-VPS>}"
  [ "$web_port" = "80" ] || address="${address}:${web_port}"
fi

printf '\nwg-warden запущен: %s\n' "$address"
printf 'Токен для первого аккаунта: %s\n' "$bootstrap_token"
printf 'Он сохранён в %s как APP_BOOTSTRAP_TOKEN.\n' "$ENV_FILE"
printf 'Управление: ./deploy/vps-service.sh {start|stop|restart|status|logs}\n'
printf 'Резервная копия: ./deploy/vps-backup.sh\n'

if grep -q '^APP_REGISTRATION_ENABLED=true' "$ENV_FILE"; then
  cat <<'HINT'

Регистрация открыта для всех: любой, кто знает адрес панели, может создать аккаунт.
Закройте её сразу после создания своего аккаунта:
  1. создайте аккаунт в панели;
  2. поставьте в .env.vps строку APP_REGISTRATION_ENABLED=false;
  3. примените настройку: ./deploy/vps-service.sh restart
HINT
else
  cat <<'HINT'

Регистрация закрыта. Первый аккаунт создаётся одноразовым setup-токеном
(APP_BOOTSTRAP_TOKEN в .env.vps), дальнейшие регистрации запрещены.
HINT
fi

if [ -z "$app_domain" ]; then
  cat <<'HINT'

Доступ по IP идёт по HTTP, без TLS: логин и токен передаются открыто.
Для постоянной работы укажите домен в APP_DOMAIN и запустите установку снова.
HINT
fi
