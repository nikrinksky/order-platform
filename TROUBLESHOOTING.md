# Troubleshooting Guide - Order Platform

## Частые проблемы и решения

---

## 1. Docker не запускается или не отвечает

### Проблема
```
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine
```

### Причины
- Docker Desktop не запущен
- Docker Desktop запущен, но ещё не полностью инициализировался
- Неправильный Docker context

### Решение
```powershell
# 1. Проверить статус Docker
docker info

# 2. Если не работает, переключить контекст
docker context use default
# или
docker context use desktop-linux

# 3. Если всё ещё не работает, перезапустить Docker Desktop
# Найти процесс и перезапустить
Get-Process Docker* | Stop-Process -Force
Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
```

---

## 2. PostgreSQL не подключается

### Проблема
```
org.postgresql.util.PSQLException: Connection refused: getsockopt
```

### Причины
- PostgreSQL контейнер не запущен
- PostgreSQL ещё не прошёл healthcheck
- Проблемы с сетью Docker

### Решение
```powershell
# 1. Проверить статус контейнеров
docker ps

# 2. Проверить логи PostgreSQL
docker logs order-platform-postgres-1

# 3. Проверить healthcheck
docker inspect --format='{{.State.Health.Status}}' order-platform-postgres-1

# 4. Проверить подключение
Test-NetConnection -ComputerName host.docker.internal -Port 5432

# 5. Если контейнер не запущен, запустить его
docker-compose up -d postgres

# 6. Подождать пока PostgreSQL готов (~30 секунд)
Start-Sleep -Seconds 30
```

---

## 3. Порт уже занят

### Проблема
```
Web server failed to start. Port 8090 was already in use.
```

### Причины
- Предыдущий экземпляр сервиса не был остановлен
- Другой процесс занимает порт

### Решение
```powershell
# 1. Найти процесс, занимающий порт
netstat -ano | findstr :8090

# 2. Завершить процесс
taskkill /PID <PID> /F

# 3. Проверить, что порт свободен
netstat -ano | findstr :8090
```

---

## 4. Переменная окружения не распознана

### Проблема
```
Could not resolve placeholder 'JWT_SECRET' in value "${JWT_SECRET}"
```

### Причины
- Переменная не установлена в PowerShell
- Переменная не передана в Maven процесс

### Решение
```powershell
# Установить переменную перед запуском
$env:JWT_SECRET = "devSecretKeyForLocalDevelopmentOnlyDoNotUseInProduction1234567890"

# Или установить в командной строке
$env:JWT_SECRET = "secret"; .\mvnw.cmd spring-boot:run -pl auth-service
```

---

## 5. Redis не подключается

### Проблема
```
io.lettuce.core.RedisConnectionException: Unable to connect to Redis
```

### Причины
- Redis контейнер не запущен
- Неверный пароль Redis
- Проблемы с сетью

### Решение
```powershell
# 1. Проверить статус Redis
docker ps --filter "name=redis"

# 2. Проверить логи Redis
docker logs order-platform-redis-1

# 3. Проверить подключение
Test-NetConnection -ComputerName host.docker.internal -Port 6379

# 4. Проверить пароль (в конфиге: dev123)
# Убедиться, что в application.yml указан правильный password
```

---

## 6. Kafka не подключается

### Проблема
```
org.apache.kafka.common.errors.TimeoutException: Topic not found
```

### Причины
- Redpanda (Kafka) контейнер не запущен
- Неверный адрес bootstrap-server
- Проблемы с сетью

### Решение
```powershell
# 1. Проверить статус Redpanda
docker ps --filter "name=redpanda"

# 2. Проверить логи Redpanda
docker logs order-platform-redpanda-1

# 3. Проверить подключение
Test-NetConnection -ComputerName host.docker.internal -Port 19092

# 4. Убедиться, что используется правильный порт (19092 для external)
# В конфиге: host.docker.internal:19092
```

---

## 7. Service Discovery не работает

### Проблема
```
java.net.UnknownHostException: auth-service
```

### Причины
- Используется имя контейнера вместо `host.docker.internal`
- Проблемы с Docker сетью

### Решение
```yaml
# В конфигурации локального запуска использовать host.docker.internal
spring:
  cloud:
    gateway:
      uri: ${AUTH_SERVICE_URL:http://host.docker.internal:8090}

# В Docker-конфигурации использовать имя контейнера
spring:
  cloud:
    gateway:
      uri: http://auth-service:8090
```

---

## 8. Healthcheck контейнера падает

### Проблема
```
order-platform-auth-service-1    unhealthy
```

### Причины
- Сервис не запустился
- Healthcheck endpoint недоступен
- Проблемы с зависимостями (PostgreSQL, Redis)

### Решение
```powershell
# 1. Проверить логи контейнера
docker logs order-platform-auth-service-1 --tail 100

# 2. Проверить статус контейнера
docker inspect --format='{{.State.Health.Status}}' order-platform-auth-service-1

# 3. Перезапустить контейнер
docker-compose restart auth-service

# 4. Или пересоздать
docker-compose up -d --force-recreate auth-service
```

---

## 9. Maven Wrapper не работает

### Проблема
```
'mvnw.cmd' is not recognized as an internal or external command
```

### Причины
- Maven Wrapper файлы не существуют
- Файлы повреждены

### Решение
```powershell
# Пересоздать Maven Wrapper
mvn wrapper:wrapper

# Или вручную создать файлы (см. .mvn/wrapper/maven-wrapper.properties)
```

---

## 10. Сервис запускается, но зависает на старте

### Проблема
```
[INFO] --- spring-boot:3.2.0:run (default-cli) @ auth-service ---
```
Процесс зависает и не завершается

### Причины
- Ожидание подключения к PostgreSQL
- Ожидание подключения к Redis
- Проблемы с Kafka
- Healthcheck не проходит

### Решение
```powershell
# 1. Проверить логи
# Для Maven-запуска смотреть вывод в консоли

# 2. Проверить подключения
Test-NetConnection -ComputerName host.docker.internal -Port 5432
Test-NetConnection -ComputerName host.docker.internal -Port 6379
Test-NetConnection -ComputerName host.docker.internal -Port 19092

# 3. Увеличить таймауты (если нужно)
# Добавить в application.yml
spring:
  datasource:
    hikari:
      connection-timeout: 60000
      validation-timeout: 10000
```

---

## 11. Spring Boot не видит profile

### Проблема
```
No active profile set, falling back to 1 default profile: "default"
```

### Причины
- Профиль не указан
- Конфигурационный файл для профиля не найден

### Решение
```powershell
# Для локального запуска через Maven
$env:SPRING_PROFILES_ACTIVE = "docker"
.\mvnw.cmd spring-boot:run

# Или в application.yml
spring:
  profiles:
    active: docker
```

---

## 12. PostgreSQLhealthcheck падает

### Проблема
```
order-platform-postgres-1    starting
# долгое время в статусе "starting"
```

### Причины
- Недостаточно памяти
- Проблемы с volumes
- Повреждённые данные

### Решение
```powershell
# 1. Проверить логи
docker logs order-platform-postgres-1

# 2. Удалить volume и пересоздать
docker-compose down -v
docker-compose up -d postgres

# 3. Проверить, что volume пустой
docker volume ls
```

---

## Быстрые команды для диагностики

```powershell
# Проверить все контейнеры
docker ps -a

# Проверить логи всех контейнеров
docker-compose logs -f

# Проверить ресурсы Docker
docker system df

# Очистить Docker (осторожно!)
docker system prune -a
```

---

## Дополнительные ресурсы

- [Docker Troubleshooting](https://docs.docker.com/desktop/troubleshoot/)
- [Spring Boot Troubleshooting](https://docs.spring.io/spring-boot/docs/current/reference/html/troubleshooting.html)
- [PostgreSQL Troubleshooting](https://www.postgresql.org/docs/current/troubleshooting.html)
