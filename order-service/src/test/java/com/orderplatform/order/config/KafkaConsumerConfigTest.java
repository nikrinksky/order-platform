package com.orderplatform.order.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KafkaConsumerConfig.
 */
class KafkaConsumerConfigTest {

    private KafkaConsumerConfig kafkaConsumerConfig;

    @BeforeEach
    void setUp() {
        kafkaConsumerConfig = new KafkaConsumerConfig();
        ReflectionTestUtils.setField(kafkaConsumerConfig, "bootstrapServers", "localhost:19092");
        ReflectionTestUtils.setField(kafkaConsumerConfig, "schemaRegistryUrl", "http://localhost:8081");
        ReflectionTestUtils.setField(kafkaConsumerConfig, "retryBackoffMs", 1000L);
        ReflectionTestUtils.setField(kafkaConsumerConfig, "retryMaxAttempts", 3L);
    }

    @Test
    void shouldCreateConsumerFactory() {
        ConsumerFactory<String, Object> factory = kafkaConsumerConfig.consumerFactory();

        assertNotNull(factory);
        Map<String, Object> config = factory.getConfigurationProperties();
        assertEquals("localhost:19092",
                config.get(org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals("earliest",
                config.get(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));
        assertEquals("http://localhost:8081", config.get("schema.registry.url"));
        assertEquals(true, config.get("specific.avro.reader"));
    }

    @Test
    void shouldCreateKafkaListenerContainerFactoryWithManualAck() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                kafkaConsumerConfig.kafkaListenerContainerFactory();

        assertNotNull(factory);
        assertNotNull(factory.getConsumerFactory());
        // An event is acknowledged only after the order state reflects it
        assertEquals(ContainerProperties.AckMode.MANUAL,
                factory.getContainerProperties().getAckMode());
    }

    /**
     * A consumed event has to continue the trace of the service that produced it, which only
     * happens when the listener container observes the record.
     */
    @Test
    void shouldEnableObservationOnTheListenerContainer() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                kafkaConsumerConfig.kafkaListenerContainerFactory();

        assertTrue(factory.getContainerProperties().isObservationEnabled());
    }

    @Test
    void shouldUseTheProvidedObservationRegistryOnTheListenerContainer() {
        io.micrometer.observation.ObservationRegistry registry =
                io.micrometer.observation.ObservationRegistry.create();
        ReflectionTestUtils.setField(kafkaConsumerConfig, "observationRegistry", registry);

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                kafkaConsumerConfig.kafkaListenerContainerFactory();

        assertSame(registry, factory.getContainerProperties().getObservationRegistry());
    }
}
