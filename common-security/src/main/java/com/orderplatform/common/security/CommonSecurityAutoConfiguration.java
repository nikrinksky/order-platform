package com.orderplatform.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Auto-configuration that makes {@link JwtService} and
 * {@link JwtAuthenticationFilter} available to every service that has
 * {@code common-security} on its classpath.
 * <p>
 * Without this, downstream services (whose {@code @SpringBootApplication}
 * scans only their own package) cannot find the shared security beans.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(JwtService.class)
@ComponentScan(basePackages = "com.orderplatform.common.security")
public class CommonSecurityAutoConfiguration {
}
