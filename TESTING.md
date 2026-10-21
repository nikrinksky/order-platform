# 🧪 Тестирование и CI/CD

## 📋 Структура тестов

### Unit-тесты (H2 in-memory)
- **Паттерн именования:** `*Test.java`
- **Расположение:** `src/test/java/...`
- **База данных:** H2 в памяти (очень быстро)
- **Запуск:** `mvn test` (Surefire plugin)
- **Пример:** `AuthServiceTest.java`, `ProductServiceTest.java`

### Integration-тесты (Testcontainers)
- **Паттерн именования:** `*IT.java` или `*IntegrationTest.java`
- **Расположение:** `src/it/java/.../integration/` (подключается через `build-helper-maven-plugin`)
- **База данных:** настоящие контейнеры PostgreSQL 15 / MongoDB 7 (Testcontainers), H2 больше не используется
- **Запуск:** `mvn verify -Pintegration-tests` (Failsafe plugin)
- **Пример:** `AuthControllerIntegrationTest.java`

Один контейнер на JVM, поднимается в `static`-блоке базового класса до создания Spring-контекста:
контексты кешируются и переиспользуются между классами, поэтому контейнер на класс перезапускал бы
базу под живым контекстом. Параметры подключения передаются через `@DynamicPropertySource` — они
перекрывают переменные окружения, так что CI не может случайно направить тесты в другую базу.

## 🚀 Запуск тестов

### Все тесты
```bash
mvn clean verify
```

### Только Unit-тесты (быстро)
```bash
mvn test
```

### Только Integration-тесты
```bash
mvn verify -Pintegration-tests -DskipUnitTests=true
```
Профиль обязателен: без него `skipITs=true` (значение по умолчанию в корневом `pom.xml`) и
failsafe-тесты просто не запускаются.

### С отчетом о покрытии
```bash
mvn verify
# Отчет: target/site/jacoco/index.html
```

## 📊 Покрытие кода (JaCoCo)

### Требования к покрытию:
- **Instructions:** 30%+
- **Branches:** 20%+

### Просмотр отчета:
```bash
# Откройте файл в браузере
open target/site/jacoco/index.html
# или
start target\site\jacoco\index.html
```

## 🔒 Безопасность

### OWASP Dependency-Check
- **Блокирует:** Уязвимости с CVSS >= 7
- **Запуск:** `mvn org.owasp:dependency-check-maven:check`
- **Подавление ложных срабатываний:** `config/dependency-check-suppression.xml`

### Trivy (в CI)
- **Блокирует:** CRITICAL и HIGH уязвимости в Docker images
- **Запуск в CI:** Автоматически

## 🔄 CI/CD Pipeline

### Этапы:
1. **Checkstyle** - проверка стиля кода (падает на нарушении, см. `config/checkstyle/sun_checks.xml`)
2. **Tests (unit + integration)** - один вызов `mvn verify -Pintegration-tests`: сначала surefire
   на H2, затем failsafe на Testcontainers
3. **Upload Reports** - surefire/failsafe/jacoco отчёты как артефакт
4. **Build + Trivy** - отдельный job с матрицей по 7 сервисам: образы собираются и сканируются
   параллельно
5. **OWASP Dependency-Check** - отдельный job, только на push в `master` и по расписанию
   (еженедельно). На PR не запускается: полная синхронизация NVD занимает часы и всё равно не
   блокировала PR

### Почему job'ы разделены
Раньше всё шло одной цепочкой на одном runner'е: checkstyle, `mvn test`, `mvn verify`, `mvn install`,
OWASP на PR и push, затем семь сборок образов и семь сканирований подряд. Проект компилировался
трижды, unit-тесты прогонялись дважды, а OWASP добавлял к каждому PR те же часы, что и к push в
`master`, оставаясь при этом `continue-on-error`. Теперь тесты идут одним вызовом, образы — матрицей,
а тяжёлое сканирование вынесено в job, который на PR не стартует вовсе.

### Запуск локально как в CI:
```bash
mvn checkstyle:check -Dcheckstyle.config.location=./config/checkstyle/sun_checks.xml
mvn verify -Pintegration-tests -T 1 -Djacoco.skip=true
```

`-T 1` обязателен для integration-тестов: `.mvn/maven.config` задаёт `-T 1C`, а каждый модуль
поднимает свои контейнеры PostgreSQL/MongoDB. При параллельной сборке нескольких модулей
Testcontainers не успевает подключиться к Docker и падает с
`Could not find a valid Docker environment`.

## 📁 Структура тестов по сервисам

```
auth-service/
├── src/test/java/
│   ├── auth/
│   │   ├── service/
│   │   │   ├── AuthenticationServiceTest.java
│   │   │   └── UserServiceTest.java
│   │   ├── security/
│   │   │   └── JwtServiceTest.java
│   │   ├── repository/
│   │   │   └── UserRepositoryTest.java
│   │   ├── controller/
│   │   │   └── AuthControllerTest.java
│   │   └── integration/
│   │       ├── AbstractIntegrationTest.java  # Базовый Integration-тест
│   │       └── AuthControllerIntegrationTest.java
│   └── AuthApplicationTests.java

order-service/
├── src/test/java/
│   ├── order/
│   │   ├── service/
│   │   │   └── OrderServiceTest.java
│   │   ├── integration/
│   │   │   ├── AbstractOrderIntegrationTest.java
│   │   │   └── OrderControllerIntegrationTest.java
│   └── OrderServiceApplicationTest.java

product-service/
├── src/test/java/
│   ├── product/
│   │   ├── service/
│   │   │   └── ProductServiceTest.java
│   │   ├── integration/
│   │   │   ├── AbstractProductIntegrationTest.java
│   │   │   └── ProductControllerIntegrationTest.java
│   └── ProductServiceApplicationTest.java

inventory-service/
├── src/test/java/
│   ├── inventory/
│   │   ├── service/
│   │   │   └── InventoryServiceTest.java
│   │   ├── integration/
│   │   │   ├── AbstractInventoryIntegrationTest.java
│   │   │   └── InventoryControllerIntegrationTest.java
│   └── InventoryServiceApplicationTest.java

notification-service/
├── src/test/java/
│   ├── notification/
│   │   ├── service/
│   │   │   └── NotificationServiceTest.java
│   │   ├── integration/
│   │   │   ├── AbstractNotificationIntegrationTest.java
│   │   │   └── NotificationControllerIntegrationTest.java
│   └── NotificationServiceApplicationTest.java

user-service/
├── src/test/java/
│   ├── user/
│   │   ├── UserServiceApplicationTest.java
│   │   ├── controller/
│   │   │   └── UserControllerTest.java
│   │   ├── service/
│   │   │   └── UserConsumerServiceTest.java
│   │   ├── repository/
│   │   │   └── UserRepositoryTest.java
│   │   ├── model/
│   │   │   └── UserTest.java
│   │   ├── integration/
│   │   │   ├── AbstractUserIntegrationTest.java
│   │   │   ├── UserIntegrationTest.java
│   │   │   └── UserControllerIntegrationTest.java
│   │   └── AbstractIntegrationTest.java
```

## 🛠️ Настройка плагинов

### Maven Surefire (Unit-тесты)
- Запускает: `*Test.java`
- Исключает: `*IT.java`, `*IntegrationTest.java`

### Maven Failsafe (Integration-тесты)
- Запускает: `*IT.java`, `*IntegrationTest.java`
- Фазы: `integration-test`, `verify`

### JaCoCo (Покрытие кода)
- Фаза `test`: генерирует отчет
- Фаза `verify`: проверяет требования к покрытию

### OWASP Dependency-Check
- Фаза `verify`: проверяет зависимости
- Блокирует при CVSS >= 7

## 💡 Советы

### Написание Unit-тестов:
- Используйте `@SpringBootTest` с `@ActiveProfiles("test")`
- Используйте Mockito для моков
- Тестируйте только логику сервиса

### Написание Integration-тестов:
- Наследуйтесь от `Abstract*IntegrationTest`
- Используйте `@Testcontainers` для запуска БД
- Тестируйте полный стек: Controller → Service → Repository

### Отладка:
- Смотрите отчеты в `target/surefire-reports/` (Unit)
- Смотрите отчеты в `target/failsafe-reports/` (Integration)
- Смотрите покрытие в `target/site/jacoco/`
