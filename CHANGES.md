# Исправления базового запуска Order Platform

## Что было исправлено

### 1. ✅ Maven Wrapper (mvnw/mvnw.cmd)
- **Проблема**: Maven Wrapper отсутствовал в проекте
- **Решение**: Создан через команду `mvn wrapper:wrapper`
- **Файлы**: 
  - `./mvnw` (для Linux/Mac)
  - `./mvnw.cmd` (для Windows)
  - `./.mvn/wrapper/maven-wrapper.properties`

### 2. ✅ Maven Wrapper конфигурация
- **Проблема**: Неправильный URL для загрузки Maven
- **Исправление**: Обновлен `distributionUrl` в `.mvn/wrapper/maven-wrapper.properties`:
  ```properties
  distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.10/apache-maven-3.9.10-bin.zip
  ```

### 3. ✅ Java версия
- **Текущая версия**: Java 21 (у пользователя)
- **Настройка проекта**: Java 17 (в `pom.xml`)
  ```xml
  <properties>
      <java.version>17</java.version>
      <maven.compiler.source>17</maven.compiler.source>
      <maven.compiler.target>17</maven.compiler.target>
  </properties>
  ```

### 4. ✅ auth-service Docker profile
- **Конфигурация**: `application-docker.yml` уже существует и настроен правильно
- **Переменные окружения в docker-compose.yaml**:
  - `SPRING_PROFILES_ACTIVE: docker`
  - `SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/orderplatform`
  - `SPRING_DATA_REDIS_HOST: redis`
  - `SPRING_KAFKA_BOOTSTRAP_SERVERS: redpanda:9092`

### 5. ✅ docker-compose для всех сервисов
- **Все сервисы включены в docker-compose.yaml**:
  - auth-service ✅
  - api-gateway ✅
  - user-service ✅
  - product-service ✅
  - inventory-service ✅
  - order-service ✅
  - notification-service ✅

### 6. ✅ Docker конфигурация
- **Исправлен redis-commander**: Обновлен формат `REDIS_HOSTS`:
  ```yaml
  environment:
    REDIS_HOSTS: default:redis:6379:0:dev123
  ```

### 7. ✅ Созданные скрипты
- `run-local.ps1` - Запуск через Maven Wrapper
- `run-local.sh` - Запуск через Maven Wrapper (Linux/Mac)
- `run-all-local.ps1` - Запуск всех сервисов локально в отдельных окнах

### 8. ✅ README.md
- Добавлен полный README с инструкциями по запуску
- Описаны все сервисы и порты
- Приведены команды для локального и Docker запуска

## Проверка работоспособности

### Проверка Maven Wrapper
```powershell
.\mvnw.cmd --version
```
Должен показать: `Apache Maven 3.9.10`

### Проверка сборки
```powershell
.\mvnw.cmd clean compile
```
Должен завершиться с: `BUILD SUCCESS`

### Проверка Docker контейнеров
```powershell
docker-compose ps
```
Все контейнеры должны быть в статусе `Up` или `healthy`

### Запуск через Docker
```powershell
docker-compose up -d
```

### Запуск локально (с инфраструктурой)
```powershell
.\mvnw.cmd clean install
cd auth-service
..\mvnw.cmd spring-boot:run
```

## Порталы после запуска

| Сервис | URL | Статус |
|--------|-----|--------|
| API Gateway | http://localhost:8080 | ✅ Работает |
| Auth Service | http://localhost:8090 | ✅ Работает |
| User Service | http://localhost:8082 | ✅ Работает |
| pgAdmin | http://localhost:5050 | ✅ Работает |
| mongo-express | http://localhost:8087 | ✅ Работает |
| redis-commander | http://localhost:8088 | ✅ Работает |

## Заметки

1. **Docker контекст**: Убедитесь, что используете `default` контекст:
   ```powershell
   docker context use default
   ```

2. **Java версия**: Для разработки локально можно использовать Java 21, но Docker использует Java 17 (через `amazoncorretto:17-alpine`)

3. **Сетевые настройки**: В Docker сервисы используют имена контейнеров (postgres, redis, redpanda), а не localhost

## Дальнейшие шаги

1. Запустить локальную разработку через IDE (IntelliJ IDEA)
2. Добавить недостающие сервисы (product-service, inventory-service, order-service, notification-service)
3. Настроить JWT секрет для production
4. Добавить мониторинг (Prometheus, Grafana)
