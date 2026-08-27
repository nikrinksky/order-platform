package com.orderplatform.user;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression test: the application context must start with Spring Security enabled.
 * <p>
 * This reproduces a local (IDEA) startup failure where the shared
 * {@code JwtAuthenticationFilter} from the {@code common-security} module
 * was not registered as a bean, so {@code SecurityConfig.securityFilterChain}
 * failed with "required a bean of type JwtAuthenticationFilter that could not be found".
 */
@TestPropertySource(properties = {
        "spring.kafka.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:security-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.sql.init.mode=never",
        "spring.security.enabled=true",
        "spring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class UserServiceSecurityContextTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret",
                () -> "test-secret-key-must-be-at-least-256-bits-long-for-hmac-sha");
    }

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoadsWithSecurityEnabled() {
        assertNotNull(context.getBean(SecurityFilterChain.class));
        assertNotNull(context.getBean("jwtAuthenticationFilter"));
    }
}