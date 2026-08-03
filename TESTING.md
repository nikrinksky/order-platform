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
- **Расположение:** `src/test/java/.../integration/`
- **База данных:** Реальные контейнеры PostgreSQL/MongoDB
- **Запуск:** `mvn verify` (Failsafe plugin)
- **Пример:** `AuthControllerIntegrationTest.java`

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
mvn verify -DskipUnitTests=true
```

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
1. **Checkstyle** - проверка стиля кода
2. **Unit Tests** - быстрый тест с H2
3. **JaCoCo Report** - отчет о покрытии
4. **Build JAR** - сборка артефактов
5. **Build Docker** - сборка образов
6. **Integration Tests** - тест с Testcontainers
7. **OWASP Check** - проверка зависимостей
8. **Trivy Scan** - сканирование образов
9. **Upload Reports** - загрузка отчетов

### Запуск локально как в CI:
```bash
mvn clean verify
```

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
│   │   ├── dto/
│   │   │   └── UserCreatedEventTest.java
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
