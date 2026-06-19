package com.orderplatform.user.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.kafka.enabled=true",
        "spring.kafka.bootstrap-servers=localhost:9092"
})
@EnableAutoConfiguration(exclude = KafkaAutoConfiguration.class)
class KafkaConfigTest {

    @Test
    void testContextLoads() {
        // Given/When/Then - просто проверяем, что контекст загружается
        // Если контекст загружается без ошибок, значит бины созданы успешно
    }
}
