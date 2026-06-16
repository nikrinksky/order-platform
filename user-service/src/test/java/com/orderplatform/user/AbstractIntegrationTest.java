package com.orderplatform.user;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.junit.jupiter.TestcontainersExtension;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(TestcontainersExtension.class)
public abstract class AbstractIntegrationTest {

    // Используем PostgreSQL контейнер для интеграционных тестов
    static PostgreSQLContainer<?> postgres;

    static {
        // Попытка запустить Testcontainers контейнеры
        boolean testcontainersStarted = false;
        try {
            postgres = new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("orderplatform")
                    .withUsername("platform")
                    .withPassword("dev123");

            postgres.start();
            testcontainersStarted = true;
            System.out.println("✓ Testcontainers PostgreSQL контейнер успешно запущен");
        } catch (Exception e) {
            System.out.println("⚠ Testcontainers не удалось запустить контейнер: " + e.getMessage());
            System.out.println("  Проверьте доступность Docker и настройки в ~/.testcontainers.properties");
            // Контейнер не запущен
            postgres = null;
        }
    }

    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        if (postgres != null && postgres.isRunning()) {
            // Используем Testcontainers контейнер
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            System.out.println("→ Используется Testcontainers PostgreSQL");
        } else {
            // Используем внешний PostgreSQL (docker-compose или CI)
            registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/orderplatform");
            registry.add("spring.datasource.username", () -> "platform");
            registry.add("spring.datasource.password", () -> "dev123");
            System.out.println("→ Используется внешний PostgreSQL (docker-compose/CI)");
        }
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }
}
