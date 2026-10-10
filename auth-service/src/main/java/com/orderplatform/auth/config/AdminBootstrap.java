/**
 * Bootstrap of the initial administrator account.
 */
package com.orderplatform.auth.config;

import com.orderplatform.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Creates the initial administrator account on startup.
 *
 * <p>Without it an administrator can only be obtained by writing SQL by hand: registration always
 * assigns {@code ROLE_USER}, so no account is allowed to manage the catalog, stock or orders and
 * the Postman collection fails on its first admin-only request. The credentials come from the
 * environment ({@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD}); when they are not set the bootstrap
 * is skipped and the application starts unchanged.
 *
 * <p>The operation is idempotent: an account that already has {@code ROLE_ADMIN} is left alone,
 * and an account that exists without the role gets it granted instead of being recreated (its
 * password is never overwritten).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AdminProperties.class)
public class AdminBootstrap implements ApplicationRunner {

    private final AdminProperties adminProperties;
    private final UserService userService;

    @Override
    public void run(ApplicationArguments args) {
        if (!adminProperties.isConfigured()) {
            log.info("Admin bootstrap skipped: ADMIN_EMAIL / ADMIN_PASSWORD are not set");
            return;
        }

        userService.ensureAdmin(
                adminProperties.getEmail(),
                adminProperties.getPassword(),
                adminProperties.getFirstName(),
                adminProperties.getLastName());
    }
}
