package com.orderplatform.order.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * The outbox entity and repository live in the starter's package, so both scans have to name it
 * next to this service's own packages. They are not declared inside the starter's
 * auto-configuration on purpose: that would make Spring Boot's JPA auto-configuration back off,
 * and they are not on the application class either, because a {@code @WebMvcTest} slice loads it
 * and would then demand an {@code EntityManagerFactory} the slice never creates.
 */
@Configuration
@EntityScan(basePackages = {"com.orderplatform.order", "com.orderplatform.outbox"})
@EnableJpaRepositories(basePackages = {"com.orderplatform.order.repository", "com.orderplatform.outbox"})
public class JpaConfig {
}
