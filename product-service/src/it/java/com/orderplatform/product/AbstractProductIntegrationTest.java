package com.orderplatform.product;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;

@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
    "spring.kafka.enabled=false",
    "spring.security.enabled=false"
})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = ProductServiceApplication.class)
@ActiveProfiles("integration-tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension.class)
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
public abstract class AbstractProductIntegrationTest {

    /**
     * A real MongoDB, not an embedded stand-in: the repository relies on Mongo query operators
     * and index behaviour, and an embedded binary downloaded at test time is both slower and a
     * recurring source of CI failures. One container per JVM, started before the shared context.
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
