package com.orderplatform.notification.config;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Consumer setup for Avro events from the schema registry.
 *
 * <p>Delivery is at-least-once, so three things are non-negotiable here:
 * <ul>
 *   <li>manual ack - the offset moves only after the notification is stored, so a crash between
 *       receive and store replays the event instead of losing it;</li>
 *   <li>a bounded retry with backoff - a failing listener blocks its partition, so the retries
 *       must end, not grow;</li>
 *   <li>a dead letter topic - when the retries end, the raw bytes are parked on
 *       {@code <topic>.DLT} instead of being dropped. The DLQ template serializes bytes, not
 *       Avro, because a record that failed to deserialize must still be parkable.</li>
 * </ul>
 */
@Configuration
public class KafkaConfig {

    /** Suffix of the dead letter topics the recoverer publishes to. */
    public static final String DLQ_SUFFIX = ".DLT";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Value("${spring.kafka.consumer.properties.retry.backoff-ms:1000}")
    private long retryBackoffMs;

    @Value("${spring.kafka.consumer.properties.retry.max-attempts:3}")
    private long retryMaxAttempts;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // ErrorHandlingDeserializer wraps the Avro deserializer, so a poison record surfaces as
        // a deserialization exception the error handler can route to the DLQ instead of as a
        // loop that re-polls the same offset forever.
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        configProps.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, KafkaAvroDeserializer.class);
        configProps.put("schema.registry.url", schemaRegistryUrl);
        // SpecificRecord classes, not GenericRecord maps: the listeners stay typed
        configProps.put("specific.avro.reader", true);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(deadLetterTemplate()),
                new FixedBackOff(retryBackoffMs, retryMaxAttempts));
        // A deserialization failure never gets better with retries; park it right away
        errorHandler.addNotRetryableExceptions(org.apache.kafka.common.errors.SerializationException.class);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    /**
     * Bytes in, bytes out: the DLQ must be able to park a record whose Avro payload cannot even
     * be read, so it deliberately does not use the Avro serializer.
     */
    @Bean
    public KafkaTemplate<String, byte[]> deadLetterTemplate() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        ProducerFactory<String, byte[]> producerFactory = new DefaultKafkaProducerFactory<>(configProps);
        return new KafkaTemplate<>(producerFactory);
    }
}
