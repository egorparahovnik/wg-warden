# WireGuard Manager

*Веб-сервис для управления WireGuard VPN на собственных VPS*

## 1. Назначение

WireGuard Manager позволяет пользователю подключить свои VPS-серверы, автоматически установить и настроить на них WireGuard, создавать конфигурации для клиентов (файл `.conf` и QR-код) и отслеживать состояние серверов и подключений из единого веб-интерфейса.

**Целевая аудитория:** частные пользователи и небольшие команды, которые сами арендуют VPS и хотят управлять VPN без ручной работы в консоли.

## 2. Основные возможности

### MVP

- Регистрация и авторизация пользователей.
- Добавление VPS по SSH (IP, порт, логин, ключ или пароль).
- Автоматическая установка и первичная настройка WireGuard.
- Создание, отключение и удаление пиров (клиентов).
- Скачивание конфигурации и отображение QR-кода для мобильных устройств.

### Мониторинг

- Статус пиров: последний handshake, онлайн/офлайн.
- Трафик по каждому пиру (принято/отправлено).
- Метрики сервера: CPU, RAM, диск, сеть.
- Графики с историей и обновление данных в реальном времени.

### Дальнейшее развитие

- Лёгкий агент на VPS вместо постоянного SSH-доступа.
- Лимиты трафика и срок действия пиров.
- Уведомления (Telegram, email) о недоступности сервера и событиях.
- Роли и командный доступ к серверам.

## 3. Технологический стек

| Слой | Технологии |
| --- | --- |
| Backend | Java 21, Spring Boot 4.x, Spring Security (JWT), Spring Data JPA, Flyway |
| SSH | Apache MINA SSHD |
| База данных | PostgreSQL (при необходимости TimescaleDB для метрик) |
| Frontend | React, TypeScript, Vite, TanStack Query, Tailwind, shadcn/ui, Recharts |
| Реальное время | SSE |
| Прочее | ZXing (QR-коды), springdoc-openapi, Docker Compose, Testcontainers |

## 4. Архитектура

```
[React SPA] <--HTTPS/REST, SSE--> [Spring Boot API] <--JDBC--> [PostgreSQL]
                                        |
                                        | SSH (позже: агент)
                                        v
                              [VPS пользователя: WireGuard]
```

**Основные модули backend:**

- **auth** — пользователи, сессии, JWT.
- **servers** — управление подключёнными VPS, проверка доступности.
- **provisioning** — установка и настройка WireGuard по SSH для поддерживаемых ОС.
- **peers** — жизненный цикл клиентов, выдача IP-адресов, генерация конфигов.
- **monitoring** — периодический опрос серверов, сбор и хранение метрик.
- **crypto** — шифрование секретов (SSH-ключи, приватные ключи).
- **audit** — журнал действий пользователей.

**Способ управления сервером:** на старте — SSH без агента (выполнение `wg`, `wg-quick`, `systemctl`). Изменения применяются через `wg set` или `wg syncconf`, чтобы не разрывать активные соединения. Метрики берутся из `wg show all dump` и системных утилит.

## 5. Модель данных (черновик)

| Таблица | Назначение | Ключевые поля | Статус |
| --- | --- | --- | --- |
| `users` | Пользователи | id, email, password_hash, created_at | есть в `V1__init.sql` |
| `servers` | Подключённые VPS | id, user_id, name, host, ssh_port, ssh_user, encrypted_credentials, host_key_fingerprint, status | есть в `V1__init.sql` |
| `wg_interfaces` | Интерфейсы WireGuard на сервере | id, server_id, name, listen_port, subnet, public_key, encrypted_private_key | есть в `V1__init.sql` |
| `peers` | Клиенты | id, interface_id, name, public_key, allowed_ip, enabled, expires_at | есть в `V1__init.sql` |
| `server_metrics` | Метрики сервера (временной ряд) | server_id, ts, cpu, ram, disk, net_rx, net_tx | этап 5 |
| `peer_metrics` | Метрики пиров (временной ряд) | peer_id, ts, rx_bytes, tx_bytes, last_handshake | этап 5 |
| `audit_log` | Журнал действий | id, user_id, action, target, ts | этап 6 |

На `(interface_id, allowed_ip)` ставится уникальный индекс для защиты от конфликтов при выдаче адресов.

## 6. Безопасность

- SSH-ключи и приватные ключи WireGuard шифруются (AES-GCM), мастер-ключ `APP_MASTER_KEY` хранится вне БД (переменная окружения, Vault или KMS).
- По возможности ключи клиента генерируются на его стороне, сервису передаётся только публичный ключ. Если ключи генерирует сервис, конфиг показывается один раз, приватный ключ клиента не сохраняется.
- Для доступа к VPS рекомендуется отдельный пользователь с sudo только на необходимые команды, а не root.
- Обязательная проверка host key при SSH-подключении.
- Rate limiting, защита от перебора паролей, журнал аудита.
- Только HTTPS, безопасные cookie и заголовки.

## 7. Основные сценарии

1. **Добавление сервера:** пользователь вводит данные VPS, сервис проверяет подключение, фиксирует host key, определяет ОС.
2. **Установка WireGuard:** сервис ставит пакеты, создаёт интерфейс, включает IP forwarding и NAT, запускает службу.
3. **Создание пира:** выбирается свободный IP, добавляется peer на сервер, пользователь получает `.conf` и QR-код.
4. **Мониторинг:** фоновая задача регулярно опрашивает серверы, сохраняет метрики, интерфейс получает обновления через SSE.

## 8. Поддерживаемые ОС (план)

- Ubuntu
- Debian

## 9. Пошаговый план разработки

Порядок этапов совпадает с зависимостями модулей: auth → servers → ssh → provisioning → peers → monitoring → frontend.

### Этап 0. Каркас (реализовано)

- Maven / Spring Boot 4.1.x, Java 21, Docker Compose с PostgreSQL 16.
- Flyway-миграция `V1__init.sql`: `users`, `servers`, `wg_interfaces`, `peers`.
- `CryptoService` (AES-256-GCM), заглушка `SecurityConfig`, пакеты-заготовки `auth`, `ssh`, `server`, `peer`, `monitoring`.
- `@EnableScheduling` в приложении (сбор метрик — на этапе 5).

**Критерий:** `./mvnw test` поднимает контекст на Testcontainers и применяет миграции.

### Этап 1. Аутентификация (реализовано)

- Entity и репозиторий `User`, регистрация и логин.
- JWT: выдача токена, фильтр (закрыть TODO в `SecurityConfig`). Argon2 уже подключён.
- Публичные маршруты: `/api/auth/**`, `/actuator/health`, OpenAPI.

**Критерий:** `POST /api/auth/register` и `POST /api/auth/login` работают; остальные `/api/**` требуют JWT; есть тесты на Testcontainers.

### Этап 2. Серверы и SSH (реализовано)

- Клиент Apache MINA SSHD: проверка host key, выполнение команд.
- CRUD серверов; SSH-креды шифруются через `CryptoService`.
- Определение Ubuntu/Debian. Статусы: `PENDING` → `ONLINE` / `ERROR`.

**Критерий:** пользователь добавляет VPS и видит ОС и статус без установки WireGuard.

### Этап 3. Provisioning WireGuard (реализовано)

- Идемпотентная установка пакетов на Ubuntu/Debian.
- Генерация ключей интерфейса, `wg0`, IP forwarding и NAT, запуск через `wg-quick` / `systemctl`.
- Последующие изменения — через `wg set` или `wg syncconf`, без разрыва активных сессий.

**Критерий:** на чистом Ubuntu/Debian поднимается интерфейс `wg0`.

Реализован endpoint `POST /api/servers/{id}/provisioning/wireguard`. Требуется доступ по SSH с `sudo -n` на команды установки и настройки; provisioning идемпотентно создаёт `wg0` с адресом `10.66.0.1/24`, портом `51820`, включает IPv4 forwarding и NAT. Существующая конфигурация `wg0`, не созданная WireGuard Manager, не перезаписывается. Приватный ключ остаётся в конфигурации на VPS и дополнительно хранится в БД только в AES-GCM шифрованном виде. Ответ содержит только публичный ключ.

### Этап 4. Пиры

- Выдача свободного IP из subnet; уникальность `(interface_id, allowed_ip)`.
- Создание, отключение, удаление пира на сервере.
- Выдача `.conf` и QR (ZXing). Приватный ключ клиента не сохранять.

**Критерий:** пользователь скачивает conf/QR, пир виден в `wg show`.

### Этап 5. Мониторинг и UI

- Миграции `server_metrics` и `peer_metrics`.
- Планировщик: `wg show all dump` + CPU/RAM/диск.
- SSE для живых обновлений.
- Frontend: React, Vite, TypeScript, TanStack Query, Tailwind, shadcn/ui, Recharts.

**Критерий:** в UI список серверов и пиров, графики и живой статус.

### Этап 6. Прод-гигиена и бэклог

- Таблица `audit_log`, rate limit логина, security headers.
- Далее из раздела 2: агент вместо постоянного SSH, лимиты трафика и срока пиров, уведомления, роли.

## 10. Запуск для разработки

### Требования

- JDK 21
- Maven Wrapper (`./mvnw` в корне репозитория)
- Docker (для PostgreSQL и Testcontainers)
- Node.js 20+ — когда появится каталог `frontend`

### База данных

```bash
docker compose up -d db
```

Контейнер `wgmanager-db`: БД/пользователь/пароль `wgmanager`, порт `5432`. Healthcheck — `pg_isready -U wgmanager`.

Остановка: `docker compose down`. Данные хранятся в томе `pgdata`.

### Секреты

Скопируйте `.env.example` в `.env` и заполните значения.

| Переменная | Назначение |
| --- | --- |
| `DB_URL` | JDBC, по умолчанию `jdbc:postgresql://localhost:5432/wgmanager` |
| `DB_USER` / `DB_PASSWORD` | учётная запись PostgreSQL |
| `APP_MASTER_KEY` | 32 байта в base64, AES-GCM для SSH- и WG-ключей |
| `JWT_SECRET` | секрет подписи JWT |

Генерация ключей:

```bash
openssl rand -base64 32   # APP_MASTER_KEY
openssl rand -base64 48   # JWT_SECRET
```

Для локальной разработки профиль `dev` задаёт тестовые ключи в `src/main/resources/application-dev.yml`. Их нельзя использовать в проде.

### Backend

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Сервис слушает порт **8080**.

| URL | Описание |
| --- | --- |
| http://localhost:8080/actuator/health | health |
| http://localhost:8080/swagger-ui.html | OpenAPI UI |

### Frontend

Каталога `frontend` пока нет (этап 5). После появления:

```bash
cd frontend && npm install && npm run dev
```

### Тесты

```bash
./mvnw test
```

Интеграционные тесты поднимают PostgreSQL 16 через Testcontainers и применяют Flyway.

### API

Этапы 1–3 реализованы. Публичные маршруты: `POST /api/auth/register`, `POST /api/auth/login`, `/actuator/health`, OpenAPI. Остальные `/api/**` требуют заголовок `Authorization: Bearer <JWT>`.

| Метод и путь | Этап | Назначение |
| --- | --- | --- |
| `POST /api/auth/register`, `POST /api/auth/login` | 1 | регистрация и JWT |
| `GET /api/me` | 1 | текущий пользователь (требует JWT) |
| `GET, POST /api/servers` | 2 | список серверов и добавление с SSH-проверкой |
| `GET, PUT, DELETE /api/servers/{id}` | 2 | чтение, изменение и удаление сервера |
| `POST /api/servers/{id}/provisioning/wireguard` | 3 | установить и настроить WireGuard |
| `/api/peers` | 4 | клиенты, conf, QR |

Для создания сервер принимает `name`, `host`, `sshPort`, `sshUser` и `credentials`, где нужно передать ровно одно из полей `password` или `privateKey` (для ключа можно передать `passphrase`). При первом SSH-подключении fingerprint ключа хоста закрепляется (TOFU); при изменении сервера новый ключ хоста не принимается. Проверка выполняет только фиксированную команду чтения `/etc/os-release` и поддерживает Ubuntu/Debian. Учётные данные хранятся в зашифрованном виде и не возвращаются API. При ошибке SSH сервер сохраняется со статусом `ERROR`.
