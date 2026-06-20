/**
 * Repository for User entity.
 */
package com.orderplatform.auth.repository;

import com.orderplatform.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for User entity.
 * Provides JPA operations for User domain model.
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * Finds user by email address.
     *
     * @param email user's email address
     * @return optional containing user if found
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks if user with given email exists.
     *
     * @param email user's email address
     * @return true if user exists
     */
    boolean existsByEmail(String email);

    /**
     * Finds user by email address if active.
     *
     * @param email user's email address
     * @return optional containing user if found and active
     */
    Optional<User> findByEmailAndIsActiveTrue(String email);
}
