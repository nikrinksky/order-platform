package com.orderplatform.auth;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = AuthApplication.class)
@ActiveProfiles("integration-tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension.class)
public abstract class AbstractIntegrationTest {

    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        // Override CI environment variables to force H2 in-memory DB
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.kafka.properties.request.timeout.ms", () -> "1000");
        registry.add("spring.kafka.properties.session.timeout.ms", () -> "1000");
        registry.add("jwt.secret", () -> "testSecretKeyForIntegrationTests1234567890");
    }
}
