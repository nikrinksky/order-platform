/**
 * User roles enumeration.
 */
package com.orderplatform.auth.model;

/**
 * User roles enumeration.
 * Defines the available roles in the system.
 */
public enum Role {

    /**
     * Standard user role.
     */
    ROLE_USER,

    /**
     * Manager role.
     */
    ROLE_MANAGER,

    /**
     * Administrator role.
     */
    ROLE_ADMIN;

    /**
     * Returns the authority string for this role.
     *
     * @return the role name as authority
     */
    public String getAuthority() {
        return this.name();
    }
}
