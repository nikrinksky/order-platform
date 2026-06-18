/**
 * Redis configuration class for Auth Service.
 * Configures Redis connection and template for token blacklisting.
 */
package com.orderplatform.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis configuration class for Auth Service.
 * Configures Redis connection and template for token blacklisting.
 */
@SuppressWarnings("DesignForExtension")
@Configuration
public class RedisConfig {

    /**
     * Redis host address.
     */
    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    /**
     * Redis port number.
     */
    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    /**
     * Redis password (optional).
     */
    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * Creates the Redis connection factory.
     *
     * @return the Lettuce connection factory
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }
        return new LettuceConnectionFactory(config);
    }

    /**
     * Creates the StringRedisTemplate for Redis operations.
     *
     * @param connectionFactory the Redis connection factory
     * @return the StringRedisTemplate bean
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
