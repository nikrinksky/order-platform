package com.orderplatform.order.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KafkaConfigTest {

    private KafkaConfig kafkaConfig;

    @BeforeEach
    void setUp() {
        kafkaConfig = new KafkaConfig();
        ReflectionTestUtils.setField(kafkaConfig, "bootstrapServers", "localhost:19092");
        ReflectionTestUtils.setField(kafkaConfig, "schemaRegistryUrl", "http://localhost:8081");
    }

    @Test
    void shouldCreateProducerFactory() {
        ProducerFactory<String, Object> factory = kafkaConfig.producerFactory();

        assertNotNull(factory);
        Map<String, Object> config = factory.getConfigurationProperties();
        assertEquals("localhost:19092",
                config.get(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals("all", config.get(org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG));
        assertEquals(true,
                config.get(org.apache.kafka.clients.producer.ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG));
        // Avro events go through the schema registry, not the JSON serializer
        assertEquals(io.confluent.kafka.serializers.KafkaAvroSerializer.class,
                config.get(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
        assertEquals("http://localhost:8081", config.get("schema.registry.url"));
    }

    @Test
    void shouldCreateKafkaTemplate() {
        KafkaTemplate<String, Object> template = kafkaConfig.kafkaTemplate();

        assertNotNull(template);
        assertNotNull(template.getProducerFactory());
    }
}