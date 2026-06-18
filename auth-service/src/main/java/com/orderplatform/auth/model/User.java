/**
 * JPA entity for user model.
 * Implements Spring Security UserDetails interface.
 */
package com.orderplatform.auth.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JPA entity for user model.
 * Implements Spring Security UserDetails interface.
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    /**
     * User's unique identifier.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * User's email address (unique).
     */
    @Column(unique = true, nullable = false)
    private String email;

    /**
     * User's password (hashed).
     */
    @Column(nullable = false)
    private String password;

    /**
     * User's first name.
     */
    @Column(name = "first_name")
    private String firstName;

    /**
     * User's last name.
     */
    @Column(name = "last_name")
    private String lastName;

    /**
     * Indicates if user account is active.
     */
    @Column(name = "is_active")
    private boolean isActive;

    /**
     * Timestamp when user was created.
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Timestamp when user was last updated.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Timestamp of last login.
     */
    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    /**
     * Set of user's roles.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private Set<Role> roles;

    /**
     * Callback method called before persisting the entity.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        isActive = true;
    }

    /**
     * Callback method called before updating the entity.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Returns user's authorities/roles.
     *
     * @return collection of granted authorities
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getAuthority()))
                .collect(Collectors.toSet());
    }

    /**
     * Returns user's email as username.
     *
     * @return user's email address
     */
    @Override
    public String getUsername() {
        return email;
    }

    /**
     * Returns if account is non-expired.
     *
     * @return always true
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Returns if account is non-locked.
     *
     * @return always true
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * Returns if credentials are non-expired.
     *
     * @return always true
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Returns if user is enabled.
     *
     * @return true if account is active
     */
    @Override
    public boolean isEnabled() {
        return isActive;
    }
}
