package com.orderplatform.user.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for KafkaConfig.
 */
class KafkaConfigTest {

    @Test
    void shouldInstantiateConfig() {
        assertDoesNotThrow(KafkaConfig::new);
    }
}