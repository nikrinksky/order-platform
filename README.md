# Order Platform

Микросервисная платформа управления заказами.

## Структура проекта

```
order-platform/
├── api-gateway/         # API Gateway
├── auth-service/        # Сервис аутентификации
├── user-service/        # Сервис пользователей
├── product-service/     # Сервис товаров
├── inventory-service/   # Сервис инвентаря
├── order-service/       # Сервис заказов
├── notification-service/# Сервис уведомлений
├── monitoring/          # Prometheus, Grafana, Loki, Vector (метрики и логи)
├── docker-compose.yaml  # Конфигурация Docker Compose
├── pom.xml              # Root POM файл
└── mvnw                 # Maven Wrapper
```

## Требования

- **Java 17** (или выше)
- **Maven 3.8+** (или используйте Maven Wrapper `mvnw`)
- **Docker & Docker Compose** (для запуска инфраструктуры и тестов)

## Быстрый старт

### Запуск через Docker Compose (Рекомендуется)

**Сначала задайте секреты.** Без `JWT_SECRET` вход не работает: `login` отвечает `401` даже на
верный пароль, и никакой причины в ответе нет — она видна только в логе auth-service. Файл
`.env` добавлен в `.gitignore`, поэтому в репозитории его нет.

```bash
# 1. Секреты (обязательно). Скопируйте шаблон и заполните значения.
cp .env.example .env
#    JWT_SECRET  - не короче 32 символов
#    POSTGRES_PASSWORD / REDIS_PASSWORD - пароли инфраструктуры
#    ADMIN_EMAIL / ADMIN_PASSWORD - первый администратор (см. ниже)

# 2. Сборка и запуск. Образы собираются из исходников (multi-stage Dockerfile), поэтому
#    предварительный "mvn package" не нужен: "docker compose build" работает на чистом клоне.
#    Сборка внутри образа повторяется до трёх раз (.mvn/docker-build.sh): зеркало Maven иногда
#    обрывает загрузку крупного jar, и без повтора падал весь образ.
#    Число попыток меняется переменной MAVEN_BUILD_ATTEMPTS.
docker compose up -d --build

# 3. Инициализация схем баз данных (для auth-service и user-service)
.\init-db-schemas.ps1

# Остановка
docker compose down

# Остановка с удалением volumes
docker compose down -v

# Просмотр логов
docker compose logs -f

# Запуск с dev-инструментами (pgAdmin, mongo-express и т.д.)
docker compose --profile dev up -d
```

> В Windows PowerShell вместо `cp` используйте `Copy-Item .env.example .env`.

### Локальный запуск (для разработки)

Для локального запуска микросервисов как Java процессов используйте PowerShell скрипты:

```powershell
# Запуск всех сервисов в отдельных окнах
.\run-all-local.ps1

# Или только инфраструктуры (если хотите запустить сервисы в IDE)
.\dev-up.ps1
```

**Для подробной информации о локальном запуске см. [LOCAL_RUN.md](LOCAL_RUN.md)**

**Для устранения проблем см. [TROUBLESHOOTING.md](TROUBLESHOOTING.md)**

## Тестирование

### Локальное тестирование (Windows)

Интеграционные тесты поднимают собственные контейнеры (Testcontainers), поэтому внешняя база для
них не нужна - достаточно запущенного Docker Desktop:

```powershell
# Unit-тесты
.\mvnw.cmd test

# Unit + интеграционные тесты на реальных Postgres/MongoDB
.\mvnw.cmd verify -Pintegration-tests
```

Профиль `integration-tests` обязателен: без него `skipITs=true` и failsafe-тесты пропускаются.

**Примечание:** На Windows нужен корректно настроенный Docker Desktop - Testcontainers работает
через его pipe. Внешние контейнеры из docker-compose больше не используются: тесты всегда поднимают
свои, чтобы результат не зависел от того, что уже запущено на машине.

### CI тестирование (Linux)

В CI (GitHub Actions) интеграционные тесты запускаются через профиль `integration-tests`:

```bash
mvn verify -Pintegration-tests
```

Testcontainers на Linux работают без проблем через Unix socket `/var/run/docker.sock`.

### Тестирование в IntelliJ IDEA

**Вариант 1: Через Maven** (Рекомендуется)

1. Откройте окно **Maven** в IntelliJ IDEA
2. Разверните `auth-service` → `Lifecycle` → `test`
3. Дважды кликните на `test` для запуска тестов

**Вариант 2: Напрямую через IDE**

1. Убедитесь что Docker Desktop запущен и доступен
2. Настройте Docker в IntelliJ IDEA:
   - **Settings** → **Build, Execution, Deployment** → **Docker**
   - Docker engine URL: `npipe:////./pipe/docker_engine`
   - Нажмите **Test Connection** для проверки
3. Запустите тесты через IDE (Run → Run...)

**Вариант 3: Только unit-тесты**

Интеграционным тестам нужен Docker (Testcontainers поднимает Postgres/MongoDB сам). Если Docker
недоступен, запускайте unit-тесты: они работают на встроенной H2 и внешних сервисов не требуют.

```powershell
.\mvnw.cmd test
```

Для подробной информации см. [IDEA_DOCKER_SETUP.md](IDEA_DOCKER_SETUP.md)

## Конфигурация

Каждый сервис имеет следующие профили:

- **default** - локальная разработка (использует localhost)
- **docker** - запуск в Docker (использует имена сервисов)
- **dev** - разработка с подключенными dev-инструментами

### Переменные окружения

Основные переменные для каждого сервиса:

- `JWT_SECRET` - **обязательна**: общий секрет подписи JWT (не короче 32 символов). Задаётся в `.env`,
  без неё `login` возвращает `401` даже на верный пароль
- `ADMIN_EMAIL` / `ADMIN_PASSWORD` - **первый администратор**. auth-service создаёт эту учётную запись
  при старте с ролью `ROLE_ADMIN`. Без неё админа можно получить только SQL-ом: регистрация всегда
  выдаёт `ROLE_USER`, а создавать товары/остатки и двигать заказы может только админ/менеджер.
  Если переменные пусты, bootstrap пропускается
- `SPRING_PROFILES_ACTIVE` - активный профиль (docker/dev)
- `SPRING_DATASOURCE_URL` - JDBC URL для PostgreSQL
- `SPRING_DATA_REDIS_HOST` - хост Redis
- `SPRING_KAFKA_BOOTSTRAP_SERVERS` - сервер Kafka (redpanda:9092 в Docker)
- `NOTIFICATION_EMAIL_ENABLED` - отправлять ли письма из notification-service. По умолчанию `false`
  (локальный запуск без почтового сервера), в `docker-compose` включено и письма уходят в MailHog
- `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` - SMTP-сервер для notification-service
  (`mailhog:1025` в Docker)
- `GATEWAY_TRUSTED_PROXIES` - адреса прокси через запятую, которым api-gateway верит заголовок
  `X-Forwarded-For`. По умолчанию пусто: порт шлюза опубликован напрямую, и заголовок от
  произвольного клиента игнорируется (иначе один клиент выдавал бы себе новый bucket rate-limiter'а
  на каждый запрос)
- `NOTIFICATION_RATE_LIMIT_ENABLED` / `NOTIFICATION_RATE_LIMIT_CAPACITY` /
  `NOTIFICATION_RATE_LIMIT_REFILL_MS` - лимит уведомлений на пользователя: подряд проходит
  `CAPACITY` писем, дальше по одному каждые `REFILL_MS` миллисекунд
- `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` - логин и пароль Grafana в локальном стеке
  (по умолчанию `admin` / `admin`)

## Порталы и инструменты

После запуска через `docker compose up -d` (профиль `dev` дополнительно поднимает pgAdmin,
mongo-express и redis-commander):

| Инструмент | URL | Логин | Пароль |
|------------|-----|-------|--------|
| API Gateway | http://localhost:8080 | - | - |
| **Swagger UI (все сервисы)** | http://localhost:8080/swagger-ui.html | - | - |
| Auth Service | http://localhost:8090 | - | - |
| User Service | http://localhost:8082 | - | - |
| pgAdmin | http://localhost:5050 | admin@orderplatform.com | admin |
| mongo-express | http://localhost:8087 | admin | admin |
| redis-commander | http://localhost:8088 | - | - |
| MailHog (письма notification-service) | http://localhost:8025 | - | - |
| Grafana (метрики и логи) | http://localhost:3000 | admin | admin |
| Prometheus (запросы и алерты) | http://localhost:9090 | - | - |
| Loki (API хранилища логов) | http://localhost:3100 | - | - |

### Swagger UI и OpenAPI

Документация есть у каждого сервиса. Единая точка входа — Swagger UI на шлюзе: он перечисляет
документы всех шести сервисов, а переключаться между ними можно в выпадающем списке сверху.

| Сервис | Документ через шлюз | Документ напрямую |
|--------|---------------------|-------------------|
| auth-service | `/api-docs/auth` | `http://localhost:8090/api-docs` |
| user-service | `/api-docs/user` | `http://localhost:8082/v3/api-docs` |
| product-service | `/api-docs/product` | `http://localhost:8089/v3/api-docs` |
| inventory-service | `/api-docs/inventory` | `http://localhost:8083/v3/api-docs` |
| order-service | `/api-docs/order` | `http://localhost:8084/v3/api-docs` |
| notification-service | `/api-docs/notification` | `http://localhost:8085/v3/api-docs` |

Схема `Bearer Authentication` объявлена во всех документах, поэтому в UI есть кнопка
**Authorize**: вставьте access-токен один раз, и он уйдёт со всеми запросами.

Выключить документацию в окружении, где публиковать её не нужно: `SWAGGER_ENABLED=false`.
Шлюз закрывает эти пути вместе с сервисами, и запрос получает `401`, а не `404` из глубины.

## Мониторинг и логи

Стек поднимается вместе с остальным через `docker compose up -d`: Prometheus, Grafana, Loki и
Vector. Отдельный профиль не нужен, потому что без метрик и логов отладка распределённой системы
превращается в чтение `docker compose logs`.

### Метрики

Каждый сервис (включая шлюз) собирается с `micrometer-registry-prometheus` и отдаёт метрики по
`/actuator/prometheus`. Prometheus опрашивает их каждые 15 секунд, конфигурация —
`monitoring/prometheus/prometheus.yml`.

Что видно в метриках:

- `http_server_requests_seconds_*` — частота, задержки и коды ответов по каждому эндпоинту;
- `jvm_memory_used_bytes`, `jvm_gc_pause_seconds_*`, `jvm_threads_live_threads` — состояние JVM;
- `up` — отвечает ли сервис на опрос вообще.

### Алерты

Правила лежат в `monitoring/prometheus/alert-rules.yml` и видны на http://localhost:9090/alerts:

| Алерт | Условие |
|-------|---------|
| `ServiceDown` | сервис не отвечает на опрос дольше 2 минут |
| `ServiceFlapping` | состояние опроса менялось больше 4 раз за 15 минут (crash loop) |
| `SlowRequests` | p95 запросов выше 150 мс в течение 5 минут (требование ТЗ) |
| `HighErrorRate` | доля ответов 5xx выше 5% в течение 5 минут |
| `JvmHeapPressure` | занято больше 90% heap дольше 10 минут |
| `NoMetrics` | метрики JVM не приходят 10 минут |

Маршрутизации уведомлений нет: для писем и мессенджеров нужен Alertmanager, в dev-стеке его нет,
поэтому алерты только отображаются в Prometheus.

### Grafana

Дашборд `Order Platform` и datasources (Prometheus + Loki) провижионятся из `monitoring/grafana`,
поэтому они версионируются вместе с кодом, а не живут в контейнере. На дашборде: состояние
сервисов, запросы в секунду, доля 5xx, p95 задержки, занятость heap и панель логов. Переменная
`Сервис` сверху фильтрует все панели сразу.

### Логи

Vector читает stdout/stderr контейнеров через Docker API (`/var/run/docker.sock`) и отправляет
строки в Loki с метками `service` (имя сервиса из compose) и `container`. Конфигурация —
`monitoring/vector/vector.toml`.

Именно Vector, а не Promtail: Promtail дочитывает файлы логов Docker-демона, а на Docker Desktop
(WSL 2) они лежат внутри виртуальной машины и в контейнер не монтируются — сбор логов молча
возвращал бы пустоту. Vector спрашивает логи у демона через API и одинаково работает на Windows,
macOS и Linux.

Примеры запросов в Grafana → Explore (datasource Loki):

```
{service="order-service"}                      # всё, что пишет order-service
{service=~".+-service"} |= "ERROR"             # ошибки всех сервисов
{service="notification-service"} |= "held back"  # срабатывания лимита уведомлений
```

## Базы данных

### PostgreSQL (auth-service)
- **Container**: `postgres`
- **Порт (host)**: `localhost:5432`
- **Порт (container)**: `5432`
- **База**: `orderplatform`
- **Схема**: `auth`
- **Пользователь**: `platform`
- **Пароль**: `dev123`

### PostgreSQL (user-service)
- **Container**: `user-db`
- **Порт (host)**: `localhost:5433`
- **Порт (container)**: `5432`
- **База**: `userdb`
- **Схема**: `user_profile`
- **Пользователь**: `userplatform`
- **Пароль**: `dev123`

### Docker Variables

#### auth-service
```yaml
environment:
  SPRING_PROFILES_ACTIVE: docker
  SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/orderplatform
  SPRING_DATASOURCE_USERNAME: platform
  SPRING_DATASOURCE_PASSWORD: dev123
  SPRING_DATA_REDIS_HOST: redis
  SPRING_DATA_REDIS_PORT: 6379
  SPRING_DATA_REDIS_PASSWORD: dev123
  SPRING_KAFKA_BOOTSTRAP_SERVERS: redpanda:9092
  SPRING_JPA_PROPERTIES_HIBERNATE_DEFAULT_SCHEMA: auth  # ✅ Используется схема auth
  JWT_SECRET: ${JWT_SECRET}
```

#### user-service
```yaml
environment:
  SPRING_PROFILES_ACTIVE: docker
  SPRING_DATASOURCE_URL: jdbc:postgresql://user-db:5432/userdb
  SPRING_DATASOURCE_USERNAME: userplatform
  SPRING_DATASOURCE_PASSWORD: dev123
  SPRING_KAFKA_BOOTSTRAP_SERVERS: redpanda:9092
  JWT_SECRET: ${JWT_SECRET}
```

### Инициализация схем

После первого запуска контейнеров выполните скрипт инициализации:
```powershell
.\init-db-schemas.ps1
```

Этот скрипт создаст:
- Схему `auth` и таблицы в `postgres:5432`
- Схему `user_profile` и таблицы в `user-db:5433`

## Разработка

### Сборка отдельного сервиса

```bash
# Сборка всего проекта
./mvnw clean install

# Сборка конкретного модуля
cd auth-service
./../mvnw clean package
```

### Запуск в IDE

1. Импортируйте проект в IntelliJ IDEA как Maven проект
2. Запустите main класс сервиса (например, `AuthApplication`)
3. Убедитесь, что `SPRING_PROFILES_ACTIVE` установлен в `dev` или `default`

### Локальный запуск через PowerShell

Для локальной разработки и отладки микросервисов запускайте их через Maven:

```powershell
# Запуск всех сервисов
.\run-all-local.ps1

# Ручной запуск одного сервиса
cd auth-service
$env:JWT_SECRET = "devSecretKeyForLocalDevelopmentOnlyDoNotUseInProduction1234567890"
.\..\mvnw.cmd spring-boot:run
```

**Для подробной информации см. [LOCAL_RUN.md](LOCAL_RUN.md)**

## Технологии

- **Backend**: Spring Boot 3.2.12, Java 17
- **Database**: PostgreSQL 15, MongoDB 7, Redis 7
- **Messaging**: Kafka (Redpanda)
- **Security**: Spring Security 6.5.9 (CVE-2026-22732 fixed)
- **Containerization**: Docker, Docker Compose
- **Monitoring**: Spring Actuator

### Архитектура

Проект использует **Event-Driven Projection Architecture**:

- **auth-service**: Хранит полную модель пользователя (с паролем) в схеме `auth` базы `orderplatform`
- **user-service**: Хранит проекцию пользователя (без пароля) в схеме `user_profile` базы `userdb`
- **Интеграция**: Через Kafka события `user.created` и `user.updated` в формате Avro
  (схемы — `common-events/src/main/avro/user-*.avsc`, реестр — Schema Registry)

Доставка событий гарантируется **транзакционным outbox**: аккаунт и его событие пишутся
в одной транзакции, а реле (`outbox-spring-boot-starter`) публикует их в Kafka с ретраями
и переводом безнадёжных записей в `<topic>.DLT`. Падение брокера больше не приводит к тому,
что зарегистрированный пользователь остаётся неизвестным user-service.

Детальное описание архитектурных изменений см. в [CHANGES.md](CHANGES.md)

## Жизненный цикл заказа (order-service)

Сценарий закрытия заказа построен на атомарном резервировании остатков и
компенсирующих действиях при сбоях:

```
создание ──reserve──> RESERVED ──pay──> PAID ──ship──> SHIPPED ──complete──> COMPLETED
                          │                │
                          └────cancel──────┴──> CANCELLED
```

Ключевые гарантии:

| Гарантия | Механизм |
|----------|----------|
| Идемпотентность создания | Заголовок `Idempotency-Key`: повторный запрос с тем же ключом → `409 Conflict` |
| Валидация переходов статусов | `OrderStatusTransitions`: недопустимый переход → `400 Bad Request`, терминальные статусы (`COMPLETED`/`CANCELLED`) не меняются |
| Атомарное резервирование | `createOrder` сразу резервирует все позиции; при отказе или сбое inventory-service уже зарезервированные позиции освобождаются (`release`), транзакция откатывается и заказ не создаётся |
| Компенсация при отмене | Отмена заказа в статусе `RESERVED` или `PAID` освобождает зарезервированный товар (best-effort, ошибки логируются); `SHIPPED` отменить нельзя — товар уже ушёл со склада |
| Списание при завершении | Переход `SHIPPED → COMPLETED` списывает резерв (`commit`: единицы уходят и из `quantity`, и из `reserved`); если inventory-service не подтвердил списание, переход отклоняется с `409 Conflict` |
| Устойчивость к сбоям inventory | `reserve` — fail-closed, `release` — best-effort, `commit` — fail-closed |

HTTP-контракт ошибок: `400` — невалидные данные/переход, `404` — заказ не найден,
`409` — дубликат идемпотентного ключа или неподтверждённое списание остатка.

## Уведомления (notification-service)

Сервис читает `order.created` и `order.status-changed`, сохраняет уведомление в MongoDB и сразу
пытается отправить письмо.

| Гарантия | Механизм |
|----------|----------|
| Запись переживает сбой почты | уведомление коммитится до отправки, а не вместе с ней: неудачная попытка планирует следующую в `NotificationRetryJob` с экспоненциальной задержкой, после `NOTIFICATION_RETRY_MAX_ATTEMPTS` запись остаётся `sent=false` с текстом ошибки |
| Идемпотентность | `eventId` Kafka-записи хранится в уведомлении, поэтому повторная доставка того же события не создаёт второе письмо; недоставленное — отправляется повторно |
| Ограничение частоты | token bucket на пользователя в Redis: `NOTIFICATION_RATE_LIMIT_CAPACITY` писем подряд, затем по одному каждые `NOTIFICATION_RATE_LIMIT_REFILL_MS`. Сообщение сверх лимита не теряется и не считается неудачной попыткой — оно переносится на следующий проход retry-джобы |
| Поведение без Redis | лимитер fail-open: при недоступном Redis письма уходят без ограничения, чтобы сбой кэша не остановил уведомления |

## Статус сервисов

- ✅ auth-service (с полной моделью пользователя в схеме `auth`)
- ✅ api-gateway
- ✅ user-service (с проекцией пользователя в схеме `user_profile`)
- ⏳ product-service
- ⏳ inventory-service
- ⏳ order-service
- ⏳ notification-service

### Архитектурные изменения

| Изменение | Статус |
|-----------|--------|
| Разделение БД (auth-service и user-service) | ✅ Завершено |
| Новые схемы: `auth` и `user_profile` | ✅ Завершено |
| Kafka события: `user.created` и `user.updated` | ✅ Завершено |
| Инициализация схем (init-db-schemas.ps1) | ✅ Завершено |
| Spring Security 6.5.9 (CVE-2026-22732 fixed) | ✅ Завершено |

---

## Устранение проблем

### Ошибка: schema "auth" does not exist

**Проблема:**
При выполнении тестов возникала ошибка `ERROR: schema "auth" does not exist` при попытке создать таблицы `auth.users` и `auth.user_roles`.

**Причина:**
Hibernate настроен на создание таблиц в схеме `auth`, но сама схема не была создана до создания таблиц.

**Решение:**
Создан файл `schema-test.sql` в `auth-service/src/test/resources/`:
```sql
-- Инициализация схемы auth для тестов
CREATE SCHEMA IF NOT EXISTS auth;
```

Обновлена конфигурация тестирования в `application-test.yml`:
```yaml
spring:
  datasource:
    initialization-mode: always
    continue-on-error: false
  jpa:
    hibernate:
      ddl-auto: create-drop
```

**Результат:**
Все 13 тестов прошли успешно:
- AuthControllerTest: 2/2 ✅
- UserRepositoryTest: 3/3 ✅
- JwtServiceTest: 4/4 ✅
- AuthenticationServiceTest: 2/2 ✅
- UserServiceTest: 2/2 ✅

---

### Ошибка: JWT token exchange vulnerability (CVE-2026-22732)

**Проблема:**
Refresh endpoint мог принять access token в качестве refresh token, поскольку не было различия в типах токенов.

**Причина:**
Метод `jwtService.isTokenValid()` не проверял тип токена, поэтому access token проходил валидацию как refresh token.

**Решение:**
1. **Обновлен `JwtService`:**
   - Добавлен claim `token_use` ("access" или "refresh") в JWT токены
   - `extractTokenType()` - извлекает тип токена
   - `isAccessToken()` - проверяет, является ли токен access token
   - `isRefreshToken()` - проверяет, является ли токен refresh token
   - `isRefreshTokenValid()` - валидирует только refresh tokens

2. **Обновлен `AuthenticationService.refreshToken()`:**
   - **ПЕРВАЯ** проверка: `jwtService.isRefreshToken(refreshToken)` (выполняется ДО обращения к БД)
   - **ВТОРАЯ** проверка: `jwtService.isRefreshTokenValid(refreshToken)`
   - `RuntimeException` с оригинальным сообщением не перехватывается в блоке `catch`

**Результат:**
- Access token больше не может использоваться для refresh endpoint
- CVE-2026-22732 исправлен
- Все тесты проходят успешно (14/14):
  - AuthenticationControllerTest: 2/2 ✅
  - UserRepositoryTest: 3/3 ✅
  - JwtServiceTest: 4/4 ✅
  - AuthenticationServiceTest: 3/3 ✅ (добавлен тест для проверки запрета access token)
  - UserServiceTest: 2/2 ✅

---

## Лицензия

MIT
