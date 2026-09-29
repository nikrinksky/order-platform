package com.orderplatform.outbox;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Test-only application class: a library has no application, but @DataJpaTest insists on finding
 * a @SpringBootConfiguration to start from.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EnableJpaRepositories(basePackageClasses = OutboxRepository.class)
public class OutboxTestApplication {
}
