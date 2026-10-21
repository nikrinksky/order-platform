package com.orderplatform.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Address a notification for a user has to be delivered to.
 *
 * <p>A projection of the auth-service user events, kept here rather than looked up over HTTP: an
 * order event carries only a user id, and reaching into user-service on every notification would
 * put a synchronous dependency in the middle of an asynchronous flow. It is the same
 * event-driven projection the rest of the platform uses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_contacts")
public class UserContact {

    /** User id from the {@code userId} claim, i.e. the value the order events carry. */
    @Id
    private String userId;

    private String email;
}
