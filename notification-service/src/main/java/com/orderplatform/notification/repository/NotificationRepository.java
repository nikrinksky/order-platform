package com.orderplatform.notification.repository;

import com.orderplatform.notification.model.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByUserId(String userId);

    List<Notification> findByUserIdAndSent(String userId, boolean sent);

    /** Looks up an earlier notification for the same delivery, see {@link Notification#getEventId()}. */
    Optional<Notification> findByEventId(String eventId);
}
