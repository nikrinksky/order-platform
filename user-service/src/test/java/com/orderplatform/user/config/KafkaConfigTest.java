package com.orderplatform.user.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that the application context loads successfully.
 * This test ensures that all configurations are properly loaded.
 */
@SpringBootTest
class KafkaConfigTest {

    @Test
    void testContextLoads() {
        // Given/When/Then - просто проверяем, что контекст загружается
        // Если контекст загружается без ошибок, значит бины созданы успешно
        assertTrue(true, "Контекст должен загружаться без ошибок");
    }
}
