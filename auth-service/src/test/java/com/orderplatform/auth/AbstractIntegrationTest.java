package com.orderplatform.auth;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.junit.jupiter.TestcontainersExtension;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(TestcontainersExtension.class)
public abstract class AbstractIntegrationTest {

    // Используем динамический порт для PostgreSQL
    static PostgreSQLContainer<?> postgres;
    static GenericContainer<?> redis;

    static {
        // Попытка запустить Testcontainers контейнеры
        boolean testcontainersStarted = false;
        try {
            postgres = new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("orderplatform")
                    .withUsername("platform")
                    .withPassword("dev123");

            redis = new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379)
                    .withCommand("redis-server", "--requirepass", "dev123");

            postgres.start();
            redis.start();
            testcontainersStarted = true;
            System.out.println("✓ Testcontainers контейнеры успешно запущены");
        } catch (Exception e) {
            System.out.println("⚠ Testcontainers не удалось запустить контейнеры: " + e.getMessage());
            System.out.println("  Проверьте доступность Docker и настройки в ~/.testcontainers.properties");
            // Контейнеры не запущены, будем использовать внешние соединения
            postgres = null;
            redis = null;
        }
    }

    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        if (postgres != null && postgres.isRunning()) {
            // Используем Testcontainers контейнеры
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.data.redis.host", redis::getHost);
            registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
            registry.add("spring.data.redis.password", () -> "dev123");
            System.out.println("→ Используются Testcontainers контейнеры");
        } else {
            // Используем внешние контейнеры (docker-compose или CI)
            registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/orderplatform");
            registry.add("spring.datasource.username", () -> "platform");
            registry.add("spring.datasource.password", () -> "dev123");
            registry.add("spring.data.redis.host", () -> "localhost");
            registry.add("spring.data.redis.port", () -> 6379);
            registry.add("spring.data.redis.password", () -> "dev123");
            System.out.println("→ Используются внешние контейнеры (docker-compose/CI)");
        }
        registry.add("jwt.secret", () -> "testSecretKeyForIntegrationTests1234567890");
        registry.add("spring.kafka.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }
}
