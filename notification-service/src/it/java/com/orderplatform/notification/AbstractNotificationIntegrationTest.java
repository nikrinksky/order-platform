package com.orderplatform.notification;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.MongoDBContainer;

@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
    "spring.kafka.enabled=false",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.security.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = NotificationServiceApplication.class)
@ActiveProfiles("integration-tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension.class)
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
public abstract class AbstractNotificationIntegrationTest {

    /**
     * A real MongoDB, not an embedded stand-in: the unique index on {@code eventId} is what makes
     * a redelivered event idempotent, and only the real server enforces it. One container per
     * JVM, started before the shared context.
     */
    protected static final MongoDBContainer MONGO =
            new MongoDBContainer("mongo:7");

    static {
        MONGO.start();
    }

    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @MockitoBean
    protected KafkaTemplate<String, Object> kafkaTemplate;
}
