package com.orderplatform.auth;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.PostgreSQLContainer;

import com.orderplatform.auth.service.TokenBlacklistService;

@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
    "spring.kafka.enabled=false"
})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = AuthApplication.class)
@ActiveProfiles("integration-tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension.class)
public abstract class AbstractIntegrationTest {

    /**
     * A real PostgreSQL, not H2.
     *
     * <p>The service relies on PostgreSQL specifics - the {@code auth} schema, {@code jsonb}
     * columns and row locking - that H2 either emulates loosely or rejects outright. A test that
     * runs on a different engine therefore cannot prove the queries work in production.
     *
     * <p>One container per JVM, started before any Spring context: the contexts are cached and
     * shared between test classes, so a per-class container would restart the database underneath
     * a live context.
     */
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("orderplatform")
                    .withUsername("platform")
                    .withPassword("dev123");

    static {
        POSTGRES.start();
    }

    @MockitoBean
    protected KafkaTemplate<String, Object> kafkaTemplate;

    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        // Dynamic properties outrank environment variables, so a CI job that exports
        // SPRING_DATASOURCE_URL cannot point the tests at some other database.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("jwt.secret", () -> "testSecretKeyForIntegrationTests1234567890");
    }
}
