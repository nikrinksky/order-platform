package com.orderplatform.auth.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPropertiesTest {

    @Test
    void isConfigured_TrueWhenEmailAndPasswordSet() {
        AdminProperties properties = new AdminProperties();
        properties.setEmail("admin@example.com");
        properties.setPassword("secret123");

        assertThat(properties.isConfigured()).isTrue();
    }

    @Test
    void isConfigured_FalseWhenEmailMissing() {
        AdminProperties properties = new AdminProperties();
        properties.setPassword("secret123");

        assertThat(properties.isConfigured()).isFalse();
    }

    @Test
    void isConfigured_FalseWhenPasswordMissing() {
        AdminProperties properties = new AdminProperties();
        properties.setEmail("admin@example.com");

        assertThat(properties.isConfigured()).isFalse();
    }

    @Test
    void isConfigured_FalseWhenBlank() {
        AdminProperties properties = new AdminProperties();
        properties.setEmail("   ");
        properties.setPassword("");

        assertThat(properties.isConfigured()).isFalse();
    }

    @Test
    void defaults_ProvideAdminNames() {
        AdminProperties properties = new AdminProperties();

        assertThat(properties.getFirstName()).isEqualTo("Admin");
        assertThat(properties.getLastName()).isEqualTo("User");
    }
}
