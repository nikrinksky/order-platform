/**
 * Database configuration class for Auth Service.
 */
package com.orderplatform.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Database configuration class for Auth Service.
 * Enables JPA auditing for automatic timestamp management.
 */
@Configuration
@EnableJpaAuditing
public class DatabaseConfig {
    // Additional database configurations if needed
}
