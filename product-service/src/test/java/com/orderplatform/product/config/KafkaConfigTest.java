package com.orderplatform.product.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KafkaConfig.
 */
class KafkaConfigTest {

    private KafkaConfig kafkaConfig;

    @BeforeEach
    void setUp() {
        kafkaConfig = new KafkaConfig();
        ReflectionTestUtils.setField(kafkaConfig, "bootstrapServers", "localhost:19092");
        ReflectionTestUtils.setField(kafkaConfig, "keySerializer",
                "org.apache.kafka.common.serialization.StringSerializer");
        ReflectionTestUtils.setField(kafkaConfig, "valueSerializer",
                "org.springframework.kafka.support.serializer.JsonSerializer");
    }

    @Test
    void shouldCreateProducerFactory() {
        ProducerFactory<String, Object> factory = kafkaConfig.producerFactory();

        assertNotNull(factory);
        assertTrue(factory instanceof org.springframework.kafka.core.DefaultKafkaProducerFactory);

        Map<String, Object> config = factory.getConfigurationProperties();
        assertEquals("localhost:19092", config.get(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals("org.apache.kafka.common.serialization.StringSerializer",
                config.get(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG));
        assertEquals("org.springframework.kafka.support.serializer.JsonSerializer",
                config.get(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
        assertEquals("all", config.get(org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG));
    }

    @Test
    void shouldCreateKafkaTemplate() {
        KafkaTemplate<String, Object> template = kafkaConfig.kafkaTemplate();

        assertNotNull(template);
        assertNotNull(template.getProducerFactory());
    }
}