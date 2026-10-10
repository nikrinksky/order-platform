/**
 * Configuration properties for the administrator bootstrap.
 */
package com.orderplatform.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Settings of the administrator bootstrap, bound from the {@code admin.*} prefix.
 *
 * <p>The bootstrap exists so that the platform can be brought up without hand-written SQL: a
 * fresh installation otherwise has no account allowed to manage the catalog, stock or orders,
 * because registration always assigns {@code ROLE_USER}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {

    /**
     * Email of the administrator account created on startup. Empty disables the bootstrap.
     */
    private String email;

    /**
     * Password of the administrator account. Required together with {@link #email}; it is only
     * used when the account is created, an existing account keeps its password.
     */
    private String password;

    /**
     * First name given to the created administrator.
     */
    private String firstName = "Admin";

    /**
     * Last name given to the created administrator.
     */
    private String lastName = "User";

    /**
     * Whether the bootstrap has enough data to run.
     *
     * <p>Both the email and the password are required. When either of them is missing the
     * application starts normally and only logs that no administrator was provisioned, so tests
     * and local runs are unaffected.
     *
     * @return true when an email and a password are configured
     */
    public boolean isConfigured() {
        return StringUtils.hasText(email) && StringUtils.hasText(password);
    }
}
