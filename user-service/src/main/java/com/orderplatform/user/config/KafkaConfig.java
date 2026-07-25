package com.orderplatform.user.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for user-service.
 * Custom ConsumerFactory to properly configure JsonDeserializer for Spring Kafka 3.x.
 */
@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = false)
    public ConsumerFactory<String, Object> consumerFactory(
            @Value("${spring.kafka.bootstrap-servers:}") String bootstrapServers) {
        // Если bootstrapServers пустой, возвращаем null - бин не будет создан
        if (bootstrapServers == null || bootstrapServers.trim().isEmpty()) {
            return null;
        }
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "user-service-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        // Configure JsonDeserializer for Spring Kafka 3.x
        // Use UserCreatedEvent.class as default type for deserialization
        JsonDeserializer deserializer = new JsonDeserializer(com.orderplatform.user.dto.UserCreatedEvent.class, false);
        deserializer.addTrustedPackages("com.orderplatform.user.dto", "com.orderplatform.auth.dto");
        deserializer.setRemoveTypeHeaders(false);
        deserializer.setUseTypeMapperForKey(false);
        
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    @ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = false)
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        // Если consumerFactory null (Kafka отключён), возвращаем null
        if (consumerFactory == null) {
            return null;
        }
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setPollTimeout(30000);
        return factory;
    }

    @Bean
    @ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = false)
    public KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry() {
        return new KafkaListenerEndpointRegistry();
    }
}
