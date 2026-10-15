package com.orderplatform.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Wires the token blacklist check for the services that have Redis configured.
 *
 * <p>Opt-in on purpose: a service without Redis (a unit test, a local run, the catalogue service
 * in an environment where revocation is handled elsewhere) keeps authenticating tokens without
 * paying for a lookup that cannot succeed.
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "security.token-blacklist", name = "enabled", havingValue = "true")
public class TokenBlacklistAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TokenBlacklistChecker.class)
    public TokenBlacklistChecker redisTokenBlacklistChecker(StringRedisTemplate redisTemplate) {
        return new RedisTokenBlacklistChecker(redisTemplate);
    }
}
