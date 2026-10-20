package com.orderplatform.inventory;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared PostgreSQL container for the integration tests of this service.
 *
 * <p>Kept separate from {@link AbstractInventoryIntegrationTest} because not every integration test
 * wants its test security configuration: {@code InventoryReservationSecurityIntegrationTest}
 * exercises the real filter chain, so it inherits only the database from here.
 *
 * <p>A real PostgreSQL, not H2: the reservation path takes a pessimistic row lock
 * ({@code FOR NO KEY UPDATE}), which is exactly what keeps two concurrent orders from overselling
 * the same stock - H2 rejects that syntax outright. One container per JVM, started before the
 * shared context.
 */
public abstract class AbstractInventoryDatabaseTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("orderplatform")
                    .withUsername("platform")
                    .withPassword("dev123");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
}
