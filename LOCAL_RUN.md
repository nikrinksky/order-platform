# Локальный запуск Order Platform

## Обзор

В проекте доступны два способа запуска:
- **Docker-запуск** через `dev-up.ps1` — всё окружение в контейнерах
- **Локальный запуск** через `run-all-local.ps1` — сервисы как локальные Java процессы

## Локальный запуск через Maven

### Требования

1. **Docker Desktop должен быть запущен** (для инфраструктуры: PostgreSQL, Redis, Kafka)
2. **Java 17** в PATH
3. **Maven Wrapper** (mvnw/mvnw.cmd) — автоматически управляется проектом

### Запуск всех сервисов

```powershell
# Установить переменные окружения и запустить сервисы в отдельных окнах
.\run-all-local.ps1
```

Этот скрипт:
- Устанавливает `JWT_SECRET` для аутентификации
- Запускает `auth-service` на порту 8090
- Запускает `api-gateway` на порту 8080
- Запускает `user-service` на порту 8082
- Каждый сервис запускается в отдельном окне PowerShell

### Ручной запуск одного сервиса

```powershell
# Для auth-service
cd auth-service
$env:JWT_SECRET = "devSecretKeyForLocalDevelopmentOnlyDoNotUseInProduction1234567890"
..\mvnw.cmd spring-boot:run

# Для api-gateway
cd api-gateway
$env:AUTH_SERVICE_URL = "http://host.docker.internal:8090"
..\mvnw.cmd spring-boot:run

# Для user-service
cd user-service
..\mvnw.cmd spring-boot:run
```

### Остановка сервисов

Закройте окна PowerShell, в которых запущены сервисы.

### Проверка работоспособности

После запуска проверьте:

- **API Gateway**: `http://localhost:8080`
- **Auth Service**: `http://localhost:8090`
- **User Service**: `http://localhost:8082`

Также проверьте endpoints Actuator:
- `http://localhost:8080/actuator/health`
- `http://localhost:8090/actuator/health`
- `http://localhost:8082/actuator/health`

## Docker-запуск через dev-up.ps1

### Требования

- Docker Desktop (запущен)

### Запуск

```powershell
.\dev-up.ps1
```

Этот скрипт:
- Запускает инфраструктуру через `docker-compose`
- Запускает микросервисы как Docker-контейнеры
- Ждёт готовности всех сервисов (15 секунд)

### Проверка

```bash
# Проверить статус контейнеров
docker ps

# Посмотреть логи конкретного сервиса
docker logs order-platform-auth-service-1 --tail 50

# Проверить здоровье контейнера
docker inspect --format='{{.State.Health.Status}}' order-platform-auth-service-1
```

## Различия подходов

| Аспект | Docker-запуск | Локальный запуск |
|--------|---------------|------------------|
| **Микросервисы** | Docker-контейнеры | Локальные Java процессы |
| **Инфраструктура** | Автоматически | Требует Docker (запущен через `dev-up.ps1`) |
| **Сеть** | Docker-сеть (`order-network`) | `host.docker.internal` |
| **Профиль Spring** | `docker` | `default` |
| **Использование** | Быстрый старт, тестирование | Локальная разработка, отладка |

## Конфигурация

### Конфигурация подключения к инфраструктуре

Все конфигурации используют `host.docker.internal` вместо `localhost`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://host.docker.internal:5432/orderplatform
    username: platform
    password: dev123
  redis:
    host: host.docker.internal
    port: 6379
    password: dev123
  kafka:
    bootstrap-servers: host.docker.internal:19092
```

### Переменные окружения

#### Для всех сервисов
- `JWT_SECRET` — секрет для JWT токенов (требуется для `auth-service`)

#### Для api-gateway
- `AUTH_SERVICE_URL` — URL auth-service (по умолчанию `http://host.docker.internal:8090`)

#### Для user-service
- `KAFKA_BOOTSTRAP_SERVERS` — URL Kafka (по умолчанию `host.docker.internal:19092`)

## Устранение проблем

### Сервис не запускается: порт занят

**Ошибка**: `Web server failed to start. Port 8090 was already in use.`

**Решение**:
```powershell
# Найти процесс, занимающий порт
netstat -ano | findstr :8090

# Завершить процесс
taskkill /PID <PID> /F
```

### Подключение к PostgreSQL не удаётся

**Ошибка**: `Connection refused: getsockopt` к `host.docker.internal:5432`

**Причины**:
1. Docker Desktop не запущен
2. PostgreSQL контейнер не запущен (`order-platform-postgres-1`)
3. PostgreSQL ещё не прошёл healthcheck

**Проверка**:
```powershell
# Проверить статус контейнеров
docker ps

# Проверить подключение
Test-NetConnection -ComputerName host.docker.internal -Port 5432
```

**Решение**:
```powershell
# Запустить инфраструктуру
.\dev-up.ps1

# Или запустить только PostgreSQL
docker-compose up -d postgres
```

### Переменная окружения не распознана

**Ошибка**: `Could not resolve placeholder 'JWT_SECRET'`

**Решение**:
```powershell
# Установить переменную перед запуском
$env:JWT_SECRET = "devSecretKeyForLocalDevelopmentOnlyDoNotUseInProduction1234567890"
```

### Сервис запускается, но не отвечает

**Причины**:
1. PostgreSQL не готов (нужно подождать healthcheck ~30 секунд)
2. Redis не доступен
3. Kafka не доступен

**Проверка логов**:
```powershell
# Для Docker-контейнеров
docker logs order-platform-auth-service-1 --tail 100

# Для локальных процессов
# Посмотреть вывод в окне PowerShell, где запущен сервис
```

## Частые сценарии

### Сценарий 1: Полный запуск окружения

```powershell
# Запустить инфраструктуру
.\dev-up.ps1

# Запустить микросервисы в Docker
docker-compose up -d

# Проверить статус
docker ps
```

### Сценарий 2: Локальная разработка одного сервиса

```powershell
# Убедиться, что инфраструктура запущена
docker ps

# Запустить только нужный сервис локально
cd auth-service
$env:JWT_SECRET = "devSecretKeyForLocalDevelopmentOnlyDoNotUseInProduction1234567890"
..\mvnw.cmd spring-boot:run
```

### Сценарий 3: Отладка через IDE

1. Запустить инфраструктуру: `.\dev-up.ps1`
2. Открыть проект в IDE (IntelliJ IDEA, VS Code)
3. Запустить `Main` класс сервиса в debug режиме
4. Установить breakpoints и отлаживать

## Тестирование

### Запуск тестов

#### Локально на Windows

Для запуска интеграционных тестов на Windows с использованием Docker Desktop:

```powershell
# Все тесты
.\mvnw.cmd test

# Только интеграционные тесты
.\mvnw.cmd test -Dtest=AuthIntegrationTest

# С профилем integration-tests
.\mvnw.cmd verify -Pintegration-tests
```

**Примечание:** Если Docker Desktop не настроен правильно для Testcontainers,
тесты автоматически будут использовать внешние контейнеры из docker-compose.

#### В IntelliJ IDEA

**Вариант 1: Через Maven** (рекомендуется)

1. Откройте окно **Maven** в IntelliJ IDEA
2. Разверните `auth-service` → `Lifecycle` → `test`
3. Дважды кликните на `test` для запуска тестов

**Вариант 2: Напрямую через IDE**

1. Убедитесь что Docker Desktop запущен и доступен
2. Настройте Docker в IntelliJ IDEA:
   - **Settings** → **Build, Execution, Deployment** → **Docker**
   - Docker engine URL: `npipe:////./pipe/docker_engine`
   - Нажмите **Test Connection** для проверки
3. Запустите тесты через IDE

**Вариант 3: Использование docker-compose**

1. Запустить необходимые контейнеры:
   ```powershell
   docker-compose up -d postgres redis
   ```
2. Запустить тесты через IDE - они будут использовать внешние контейнеры

Для подробной информации о настройке Docker в IntelliJ IDEA см. [IDEA_DOCKER_SETUP.md](IDEA_DOCKER_SETUP.md)

#### В CI (GitHub Actions)

В CI интеграционные тесты запускаются через профиль `integration-tests`:

```bash
mvn verify -Pintegration-tests
```

Testcontainers на Linux работают без проблем через Unix socket `/var/run/docker.sock`.

### Использование Testcontainers

В проекте используется Testcontainers 1.19.3 для изолированных интеграционных тестов:

- **PostgreSQL** 15-alpine
- **Redis** 7-alpine

Тесты автоматически запускают контейнеры перед каждым тестом и останавливают после завершения.

**Архитектура тестов:**
- `AbstractIntegrationTest` - базовый класс для интеграционных тестов
- `AuthIntegrationTest` - пример интеграционных тестов auth-service
- Тесты используют `@Testcontainers` и `@SpringBootTest` аннотации

### Устранение проблем с тестами

#### Ошибка: "Could not find a valid Docker environment"

**Причина:** Testcontainers не может подключиться к Docker Desktop

**Решение:**
1. Убедитесь что Docker Desktop запущен
2. Настройте Docker в IntelliJ IDEA (см. выше)
3. Или используйте docker-compose контейнеры (см. выше)

#### Ошибка: "Connection refused" к PostgreSQL/Redis

**Причина:** Контейнеры не запущены или не готовы

**Решение:**
```powershell
# Проверить статус контейнеров
docker ps

# Запустить недостающие контейнеры
docker-compose up -d postgres redis

# Подождать готовность (30-60 секунд)
```

#### Тесты запускаются, но падают

**Причина:** База данных не готова или контейнеры не запущены

**Решение:**
1. Убедитесь что контейнеры запущены и готовы
2. Проверьте логи контейнеров: `docker logs order-platform-postgres-1`
3. Увеличьте таймаут подключения в `application-test.yml`

## Дополнительные ресурсы

- [Docker Compose documentation](https://docs.docker.com/compose/)
- [Spring Boot documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Maven Wrapper documentation](https://maven.apache.org/wrapper/)
