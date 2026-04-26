package com.orderplatform.auth.model;

public enum Role {
    ROLE_USER,
    ROLE_MANAGER,
    ROLE_ADMIN;

    public String getAuthority() {
        return this.name();
    }
}