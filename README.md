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

```bash
# Запуск всей инфраструктуры + сервисов
docker-compose up -d

# Инициализация схем баз данных (для auth-service и user-service)
.\init-db-schemas.ps1

# Остановка
docker-compose down

# Остановка с удалением volumes
docker-compose down -v

# Просмотр логов
docker-compose logs -f

# Запуск с dev-инструментами (pgAdmin, mongo-express и т.д.)
docker-compose --profile dev up -d
```

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

Для запуска интеграционных тестов локально на Windows:

1. Убедитесь что Docker Desktop запущен и контейнеры доступны:
   ```powershell
   docker ps
   ```

2. Запустите необходимые контейнеры через docker-compose (если не запущены):
   ```powershell
   docker-compose up -d postgres redis
   ```

3. Запустите тесты через Maven:
   ```powershell
   # Все тесты
   .\mvnw.cmd test
   
   # Только интеграционные тесты
   .\mvnw.cmd test -Dtest=AuthIntegrationTest
   ```

**Примечание:** На Windows может потребоваться корректная настройка Docker Desktop.
Если Testcontainers не может подключиться к Docker, тесты будут использовать внешние контейнеры из docker-compose.

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

**Вариант 3: Использование docker-compose для тестов**

Если IntelliJ IDEA не может подключиться к Docker, можно:

1. Запустить необходимые контейнеры через docker-compose:
   ```powershell
   docker-compose up -d postgres redis
   ```

2. Запустить тесты через IDE - они будут использовать внешние контейнеры

Для подробной информации см. [IDEA_DOCKER_SETUP.md](IDEA_DOCKER_SETUP.md)

## Конфигурация

Каждый сервис имеет следующие профили:

- **default** - локальная разработка (использует localhost)
- **docker** - запуск в Docker (использует имена сервисов)
- **dev** - разработка с подключенными dev-инструментами

### Переменные окружения

Основные переменные для каждого сервиса:

- `SPRING_PROFILES_ACTIVE` - активный профиль (docker/dev)
- `SPRING_DATASOURCE_URL` - JDBC URL для PostgreSQL
- `SPRING_DATA_REDIS_HOST` - хост Redis
- `SPRING_KAFKA_BOOTSTRAP_SERVERS` - сервер Kafka (redpanda:9092 в Docker)

## Порталы и инструменты

После запуска через `docker-compose --profile dev up -d`:

| Инструмент | URL | Логин | Пароль |
|------------|-----|-------|--------|
| API Gateway | http://localhost:8080 | - | - |
| Auth Service | http://localhost:8090 | - | - |
| User Service | http://localhost:8082 | - | - |
| pgAdmin | http://localhost:5050 | admin@orderplatform.com | admin |
| mongo-express | http://localhost:8087 | admin | admin |
| redis-commander | http://localhost:8088 | - | - |

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
- **Интеграция**: Через Kafka события `user.created` и `user.updated`

Детальное описание архитектурных изменений см. в [CHANGES.md](CHANGES.md)

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
| Spring Security 6.5.9 (CVE-2026-22732) | ✅ Завершено |

## Лицензия

MIT
