package com.orderplatform.notification.repository;

import com.orderplatform.notification.model.UserContact;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Access to the user id to email projection.
 */
public interface UserContactRepository extends MongoRepository<UserContact, String> {
}
