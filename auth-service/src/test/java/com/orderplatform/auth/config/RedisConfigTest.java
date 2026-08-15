package com.orderplatform.auth.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class RedisConfigTest {

    private RedisConfig redisConfig;

    @BeforeEach
    void setUp() {
        redisConfig = new RedisConfig();
        ReflectionTestUtils.setField(redisConfig, "redisHost", "localhost");
        ReflectionTestUtils.setField(redisConfig, "redisPort", 6379);
        ReflectionTestUtils.setField(redisConfig, "redisPassword", "secret");
    }

    @Test
    void shouldCreateConnectionFactory() {
        LettuceConnectionFactory factory = redisConfig.redisConnectionFactory();

        assertNotNull(factory);
        RedisStandaloneConfiguration config = factory.getStandaloneConfiguration();
        assertEquals("localhost", config.getHostName());
        assertEquals(6379, config.getPort());
        assertTrue(config.getPassword().isPresent());
    }

    @Test
    void shouldCreateTemplate() {
        LettuceConnectionFactory factory = redisConfig.redisConnectionFactory();

        StringRedisTemplate template = redisConfig.stringRedisTemplate(factory);

        assertNotNull(template);
    }

    @Test
    void shouldCreateFactoryWithoutPasswordWhenEmpty() {
        ReflectionTestUtils.setField(redisConfig, "redisPassword", "");

        LettuceConnectionFactory factory = redisConfig.redisConnectionFactory();

        assertNotNull(factory);
        assertFalse(factory.getStandaloneConfiguration().getPassword().isPresent());
    }
}