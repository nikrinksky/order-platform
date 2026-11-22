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
├── monitoring/          # Prometheus, Grafana, Loki, Vector, Jaeger (метрики, логи, трейсы)
├── helm/order-platform/ # Helm-чарт: сервисы, инфраструктура и мониторинг для k3d
├── .secrets/dev/        # секреты dev-окружения для кластера (в git только шаблон)
├── docker-compose.yaml  # Конфигурация Docker Compose
├── pom.xml              # Root POM файл
└── mvnw                 # Maven Wrapper
```

## Требования

- **Java 17** (или выше)
- **Maven 3.8+** (или используйте Maven Wrapper `mvnw`)
- **Docker & Docker Compose** (для запуска инфраструктуры и тестов)
- **k3d, kubectl, Helm 3** — только для запуска в Kubernetes (см. раздел «Kubernetes»)

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

### Запуск в Kubernetes (k3d + Helm)

Тот же стек разворачивается в локальный кластер k3d одной командой. Нужны `k3d`, `kubectl` и
Helm 3 (`winget install k3d.k3d Kubernetes.kubectl Helm.Helm`), Docker Desktop — как и для compose.

```bash
# 1. Освободите порты: compose и кластер публикуют одни и те же
docker compose down

# 2. Секреты: JWT_SECRET и первый администратор. Нужны до установки: auth-service объявляет
#    jwt.secret как ${JWT_SECRET} без значения по умолчанию и не стартует без него
cp .secrets/dev/platform.env.example .secrets/dev/platform.env   # заполните значения

# 3. Кластер, образы, namespace, Secret и чарт
make k3d-up

# Применить изменённые секреты к уже работающему кластеру (перезапускает поды)
make inject-secrets

# Удаление кластера вместе с данными
make k3d-down
```

В PowerShell те же шаги выполняют `./k3d-up.ps1`, `./inject-secrets.ps1` и `./k3d-down.ps1`.
Порты хоста можно переопределить, если 8080/3000/9090/16686/8025 заняты:
`make k3d-up GATEWAY_PORT=9080 GRAFANA_PORT=3300`.

После запуска доступны: шлюз и Swagger UI на http://localhost:8080, Grafana на
http://localhost:3000, Prometheus на http://localhost:9090, Jaeger на http://localhost:16686,
MailHog на http://localhost:8025.

**Что поднимается.** Один Helm-чарт `helm/order-platform` в namespace `order-platform`:
7 сервисов, 4 инстанса PostgreSQL (`postgres`, `user-db`, `inventorydb`, `orderdb`), MongoDB,
Redis, Redpanda, Schema Registry, MailHog и весь мониторинг — Prometheus с теми же правилами
алертов, Grafana с теми же datasources и дашбордом, Loki, Jaeger и Vector.

**Почему сервисы не пришлось менять.** Имена Service в чарте повторяют имена контейнеров в
compose (`postgres`, `user-db`, `mongodb`, `redpanda`, …), поэтому образы запускаются с уже
существующим профилем `docker` и ни один `application.yml` не правился.

**Образы не тянутся из registry.** `make k3d-up` собирает их через `docker compose build` и
загружает в кластер скриптом `scripts/import-images.{sh,ps1}`: он идёт через containerd
(`docker save` → `ctr images import --platform linux/amd64`). Штатный `k3d image import` для
multi-arch образов (redpanda, schema-registry, mailhog) молча падает с `content digest not found`,
и поды потом полчаса висят в `ContainerCreating`, пытаясь скачать образ из Docker Hub.
Вместе с сервисами импортируются и образы инфраструктуры и мониторинга, поэтому первый старт
не зависит от скорости Docker Hub.

**Секреты.** `make k3d-up` создаёт Secret `platform-secrets` из `.secrets/dev/platform.env`
(файл в `.gitignore`, в репозитории только шаблон) до установки чарта: без `JWT_SECRET`
auth-service не стартует. `make inject-secrets` делает то же самое и перезапускает поды — им
нужен рестарт, чтобы прочитать изменившиеся значения.

**Данные.** Тома — PVC на классе `local-path`, который k3d ставит по умолчанию: данные переживают
перезапуск пода, но удаляются вместе с кластером (`k3d cluster delete`).

**Отличия от compose.** Vector в кластере читает не Docker-сокет, а файлы подов через API
(`kubernetes_logs` с фильтром по метке `app.kubernetes.io/part-of=order-platform`); Redis и
PostgreSQL имеют по одному тому на инстанс и стратегию `Recreate`, потому что том `ReadWriteOnce`
нельзя смонтировать в два пода. Полному стеку нужно около 7 ГБ памяти Docker Desktop.

**Две ловушки Kubernetes, из-за которых чарт выглядит странно.**

- `enableServiceLinks: false` у подов. Kubernetes сам создаёт переменные `<SERVICE>_PORT` и
  `<SERVICE>_SERVICE_HOST` для каждого Service в namespace, и Service с именем `schema-registry`
  даёт `SCHEMA_REGISTRY_PORT` — а стартовый скрипт образа Schema Registry считает эту переменную
  устаревшей настройкой порта и завершается с кодом 1.
- `startupProbe` у сервисов. Контекст Spring Boot на узле с ограниченными ресурсами поднимается
  минуту-две, и liveness-проба успевала убить под до того, как приложение начнёт отвечать. Со
  startup-пробой остальные две ждут первого успешного ответа.

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

### Проверки Helm-чарта

Отдельная job `helm` в пайплайне смотрит на `helm/order-platform`: без неё сломанный шаблон
доходил до `dev`, потому что ни одна другая проверка чарт не читает.

```bash
helm lint --strict helm/order-platform

helm template order-platform helm/order-platform --namespace order-platform > /tmp/default.yaml
helm template order-platform helm/order-platform --namespace order-platform \
  --set monitoring.prometheus.enabled=false --set monitoring.grafana.enabled=false \
  --set monitoring.loki.enabled=false --set monitoring.jaeger.enabled=false \
  --set monitoring.vector.enabled=false > /tmp/minimal.yaml

kubeconform -strict -summary -kubernetes-version 1.31.5 /tmp/default.yaml /tmp/minimal.yaml
```

Локально те же шаги выполняет `make helm-check`. `kubeconform` в стек не входит — его нужно
поставить отдельно (`winget install Kubernetes.kubeconform` или бинарник из
[релизов](https://github.com/yannh/kubeconform/releases)).

Что именно ловят проверки:

- `helm lint --strict` — ошибки и **предупреждения** шаблонов: без `--strict` Helm печатает
  `WARNING` и всё равно выходит с кодом 0, поэтому пропущенное значение проходило молча;
- два рендера — ветки `{{- if .Values.monitoring.*.enabled }}`: с включённым мониторингом
  проверяется основной путь, с выключенным — тот, где объекты не создаются, и ошибка вроде
  ссылки на значение из включённой ветки видна только во втором;
- `kubeconform -strict` — соответствие схеме Kubernetes: неизвестное поле (`nodePorts` вместо
  `nodePort`) считается ошибкой. Версия схемы зафиксирована (`1.31.5`, как в k3d), чтобы выход
  новой версии Kubernetes не ломал чарт, который в кластер ставится.

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
- `OTEL_EXPORTER_OTLP_ENDPOINT` - куда сервисы отправляют трейсы по OTLP/HTTP. По умолчанию
  `http://localhost:4318/v1/traces` (локальный запуск), в `docker-compose` — `http://jaeger:4318/v1/traces`
- `OTEL_TRACING_SAMPLING_PROBABILITY` - доля запросов, попадающих в трейсы (0.0–1.0).
  По умолчанию `1.0`: в dev пишутся все трейсы, в проде значение уменьшают
- `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` в кластере приходят из Secret `platform-secrets`,
  который создаётся из `.secrets/dev/platform.env` командой `make inject-secrets` (шаблон —
  `.secrets/dev/platform.env.example`)

## Порталы и инструменты

После запуска через `docker compose up -d` (профиль `dev` дополнительно поднимает pgAdmin,
mongo-express и redis-commander). В Kubernetes адреса те же — кластер публикует те же порты хоста:

| Инструмент | URL | Логин | Пароль |
|------------|-----|-------|--------|
| API Gateway | http://localhost:8080 | - | - |
| **Swagger UI (все сервисы)** | http://localhost:8080/swagger-ui.html | - | - |
| Auth Service | http://localhost:8090 | - | - |
| User Service | http://localhost:8082 | - | - |
| pgAdmin (только compose) | http://localhost:5050 | admin@orderplatform.com | admin |
| mongo-express (только compose) | http://localhost:8087 | admin | admin |
| redis-commander (только compose) | http://localhost:8088 | - | - |
| MailHog (письма notification-service) | http://localhost:8025 | - | - |
| Grafana (метрики и логи) | http://localhost:3000 | admin | admin |
| Prometheus (запросы и алерты) | http://localhost:9090 | - | - |
| Loki (API хранилища логов, только compose) | http://localhost:3100 | - | - |
| Jaeger (трейсы) | http://localhost:16686 | - | - |

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

Стек поднимается вместе с остальным через `docker compose up -d`: Prometheus, Grafana, Loki,
Vector и Jaeger. Отдельный профиль не нужен, потому что без метрик, логов и трейсов отладка
распределённой системы превращается в чтение `docker compose logs`.

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

Дашборд `Order Platform` и datasources (Prometheus + Loki + Jaeger) провижионятся из
`monitoring/grafana`, поэтому они версионируются вместе с кодом, а не живут в контейнере.
На дашборде: состояние сервисов, запросы в секунду, доля 5xx, p95 задержки, занятость heap,
панель логов и ссылки на трейсы. Переменная `Сервис` сверху фильтрует все панели сразу.

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

### Трассировка

Каждый сервис подключён к Micrometer Tracing с мостом OpenTelemetry и отправляет спаны по
OTLP/HTTP в Jaeger (`jaegertracing/all-in-one`, UI — http://localhost:16686).

Что попадает в трейс:

- **входящие HTTP-запросы** — спан на каждый запрос; заголовок `traceparent` от вызывающего
  продолжает его трейс, поэтому запрос через шлюз и работа сервиса лежат в одном трейсе;
- **исходящий вызов order-service → inventory-service** — RestTemplate получает
  `ObservationRegistry`, контекст уходит в заголовке, и резервирование остатка видно в том же
  трейсе, что и создание заказа;
- **публикация в Kafka** — `spring.kafka.template.observation-enabled: true` (auth-service,
  user-service) и явные `setObservationRegistry` / `setObservationEnabled` в `KafkaConfig`
  order-, inventory- и product-service;
- **чтение из Kafka** — listener-контейнеры order-service и notification-service создают спаны и
  продолжают трейс из заголовков сообщения, у user-service это включает
  `spring.kafka.listener.observation-enabled`.

Настройки лежат в `application.yml` каждого сервиса:

```yaml
management:
  tracing:
    sampling:
      probability: ${OTEL_TRACING_SAMPLING_PROBABILITY:1.0}
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}
```

Как посмотреть трейс: отправьте запрос через шлюз (`POST /api/orders` и подобное), откройте
Jaeger UI, выберите сервис и нажмите **Find Traces**. В трейсе будут спаны шлюза, order-service,
inventory-service и публикаций в Kafka.

Jaeger не является зависимостью старта: если коллектор недоступен, сервис продолжает работать,
а экспортёр пишет предупреждения в лог. Ссылки на трейсы есть на дашборде Grafana, а в Explore
они открываются через datasource `Jaeger`.

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
