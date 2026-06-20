/**
 * Main application class for Auth Service.
 */
package com.orderplatform.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for Auth Service.
 */
@SuppressWarnings({"PMD_FINAL_CLASS", "PMD_FINAL_PARAMETERS", "checkstyle:FinalParameters", "checkstyle:MissingJavadocMethod"})
@SpringBootApplication
public class AuthApplication {

    /**
     * Main entry point for the application.
     */
    AuthApplication() {
    }

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
