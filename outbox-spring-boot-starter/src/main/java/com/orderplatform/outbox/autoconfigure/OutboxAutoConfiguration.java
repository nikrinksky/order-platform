package com.orderplatform.outbox.autoconfigure;

import com.orderplatform.outbox.DeadLetterPublisher;
import com.orderplatform.outbox.EventPayloadCodec;
import com.orderplatform.outbox.KafkaDeadLetterPublisher;
import com.orderplatform.outbox.KafkaOutboxPublisher;
import com.orderplatform.outbox.OutboxProperties;
import com.orderplatform.outbox.OutboxPublisher;
import com.orderplatform.outbox.OutboxRelay;
import com.orderplatform.outbox.OutboxRepository;
import com.orderplatform.outbox.OutboxService;
import org.apache.avro.specific.SpecificRecordBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wires the transactional outbox.
 *
 * <p>Two things are expected from the service that uses this starter, both of them explicit and
 * both of them one line:
 *
 * <ul>
 *   <li>{@code outbox.events} has to name the event classes the relay may publish. Nothing is
 *       scanned, so an event the relay cannot decode fails at startup instead of during a
 *       broker incident.</li>
 *   <li>the outbox entity and repository have to be inside the service's
 *       {@code @EntityScan}/{@code @EnableJpaRepositories}. They are not registered from here on
 *       purpose: declaring them in an auto-configuration makes Spring Boot's own JPA
 *       auto-configuration back off, and the service silently loses its own repositories.</li>
 * </ul>
 */
@AutoConfiguration(after = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
        KafkaAutoConfiguration.class})
@ConditionalOnClass({SpecificRecordBase.class, JpaRepository.class})
@ConditionalOnProperty(prefix = "outbox", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(OutboxProperties.class)
@EnableRetry(proxyTargetClass = true)
@EnableScheduling
public class OutboxAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OutboxAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public EventPayloadCodec outboxEventPayloadCodec(OutboxProperties properties) {
        Map<String, Class<? extends SpecificRecordBase>> eventTypes = new LinkedHashMap<>();
        properties.getEvents().forEach((schema, className) -> eventTypes.put(schema, load(schema, className)));
        if (eventTypes.isEmpty()) {
            log.warn("outbox.events is empty: the relay cannot publish anything until the event "
                    + "classes are listed there");
        }
        return new EventPayloadCodec(eventTypes);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxService outboxService(OutboxRepository repository, EventPayloadCodec codec) {
        return new OutboxService(repository, codec);
    }

    @Bean
    @ConditionalOnBean(KafkaTemplate.class)
    @ConditionalOnMissingBean(OutboxPublisher.class)
    public KafkaOutboxPublisher kafkaOutboxPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                                     EventPayloadCodec codec,
                                                     OutboxProperties properties) {
        return new KafkaOutboxPublisher(kafkaTemplate, codec, properties.getRelay().getSendTimeoutMs());
    }

    @Bean
    @ConditionalOnBean(KafkaTemplate.class)
    @ConditionalOnMissingBean(DeadLetterPublisher.class)
    public KafkaDeadLetterPublisher kafkaDeadLetterPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                                             EventPayloadCodec codec,
                                                             OutboxProperties properties) {
        return new KafkaDeadLetterPublisher(kafkaTemplate, codec, properties.getDeadLetterSuffix(),
                properties.getRelay().getSendTimeoutMs());
    }

    @Bean
    @ConditionalOnBean(OutboxPublisher.class)
    @ConditionalOnMissingBean
    public OutboxRelay outboxRelay(OutboxRepository repository,
                                   OutboxPublisher publisher,
                                   ObjectProvider<DeadLetterPublisher> deadLetterPublisher,
                                   OutboxProperties properties,
                                   PlatformTransactionManager transactionManager) {
        return new OutboxRelay(repository, publisher, deadLetterPublisher, properties, transactionManager);
    }

    private static Class<? extends SpecificRecordBase> load(String schema, String className) {
        try {
            Class<?> type = Class.forName(className);
            if (!SpecificRecordBase.class.isAssignableFrom(type)) {
                throw new IllegalStateException("Event type '" + schema + "' (" + className
                        + ") must extend SpecificRecordBase to be published through the outbox");
            }
            return type.asSubclass(SpecificRecordBase.class);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Event type '" + schema + "' points at a class that is "
                    + "not on the classpath: " + className, e);
        }
    }
}
