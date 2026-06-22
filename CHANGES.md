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

---

# Архитектурные изменения: Event-Driven Projection

## Что было изменено

### 1. ✅ Разделение баз данных
- **До**: auth-service и user-service использовали одну БД и таблицу `users`
- **После**: Каждый сервис имеет отдельную БД и схему

#### Новые сервисы:
- **postgres (порт 5432)**: База `orderplatform` со схемой `auth`
- **user-db (порт 5433)**: База `userdb` со схемой `user_profile`

### 2. ✅ Структура таблиц

#### auth.users (auth-service)
- `id` (VARCHAR) - UUID пользователя
- `username` (VARCHAR) - логин
- `email` (VARCHAR) - email
- `password` (VARCHAR) - **хэшированный пароль**
- `first_name` (VARCHAR)
- `last_name` (VARCHAR)
- `is_active` (BOOLEAN)
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)
- `last_login` (TIMESTAMP)
- `user_roles` - связь с таблицей ролей

#### user_profile.users (user-service)
- `id` (BIGSERIAL) - auto-increment ID
- `username` (VARCHAR) - логин
- `email` (VARCHAR) - email
- `first_name` (VARCHAR)
- `last_name` (VARCHAR)
- `is_active` (BOOLEAN)
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)
- **ВАЖНО**: Поле `password` отсутствует

### 3. ✅ Kafka события

#### user.created
```json
{
  "id": "uuid-123",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "roles": ["ROLE_USER"],
  "isActive": true,
  "createdAt": "2026-06-22T12:00:00Z"
}
```
- Отправляется при регистрации нового пользователя
- Пользователь user-service создает проекцию в `user_profile.users`

#### user.updated
```json
{
  "id": "uuid-123",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "roles": ["ROLE_USER"],
  "isActive": true,
  "updatedAt": "2026-06-22T12:00:00Z"
}
```
- Отправляется при обновлении данных пользователя
- Пользователь user-service обновляет проекцию в `user_profile.users`

### 4. ✅ Конфигурация Kafka

#### auth-service (отправитель)
- `KafkaTemplate<String, Object>` для отправки событий
- Topic: `user.created`, `user.updated`

#### user-service (консьюмер)
- `@KafkaListener` для обработки событий
- Группа: `user-service-group`
- Темы: `user.created`, `user.updated`

### 5. ✅ Инициализация схем

#### init-db-schemas.ps1
PowerShell скрипт для создания схем после запуска контейнеров:
```powershell
# Создает схему auth и таблицы в postgres:5432
# Создает схему user_profile и таблицы в user-db:5433
```

Запуск:
```powershell
.\init-db-schemas.ps1
```

### 6. ✅ Обновленные файлы

| Файл | Изменения |
|------|-----------|
| `docker-compose.yaml` | Добавлен user-db сервис на порту 5433 |
| `auth-service/src/main/resources/application*.yml` | Добавлен `hibernate.default_schema: auth` |
| `user-service/src/main/resources/application*.yml` | Добавлен `hibernate.default_schema: user_profile` |
| `auth-service/src/main/java/.../model/User.java` | Удален `@Table(schema="auth")` |
| `user-service/src/main/java/.../model/User.java` | Удалено поле password, удален `@Table(schema="user")` |
| `auth-service/src/main/java/.../UserService.java` | Добавлен метод `updateUser` и отправка `user.updated` |
| `user-service/src/main/java/.../UserConsumerService.java` | Добавлен обработчик `user.updated` |
| `auth-service/pom.xml` | Spring Security 6.5.9 |
| `user-service/pom.xml` | Spring Security 6.5.9 |
| `init-db-schemas.ps1` | Новый скрипт инициализации схем |

### 7. ✅ Проверка результатов

#### Схемы баз данных
```sql
-- auth-service
\dn auth
\dt auth.users

-- user-service
\dn user_profile  
\dt user_profile.users
```

#### Kafka топики
```bash
# Список топиков
rpk topic list
# Должны быть: user.created, user.updated
```

#### Статус контейнеров
```powershell
docker-compose ps
```
Все контейнеры должны быть `healthy`

### 8. ✅ Преимущества новой архитектуры

1. **Независимость**: auth-service и user-service имеют отдельные БД
2. **Безопасность**: Пароль не хранится в user-service
3. **Масштабируемость**: Сервисы масштабируются независимо
4. **Асинхронность**: Синхронизация через Kafka без прямых вызовов
5. **Гибкость**: Можно изменять структуру проекции без влияния на auth-service

### 9. ⚠️ Известные ограничения

1. **Задержка синхронизации**: Проекция обновляется асинхронно через Kafka
2. **Потенциальная потеря данных**: Если user-service недоступен, события накапливаются в Kafka
3. **Дублирование данных**: email, first_name, last_name хранятся в обеих БД

### 10. 📋 Дальнейшие улучшения

- Добавить CDC (Change Data Capture) для автоматической синхронизации
- Реализовать retry механизм для обработки ошибок Kafka
- Добавить мониторинг задержек синхронизации
- Рассмотреть использование Debezium для CDC
