package com.orderplatform.notification.repository;

import com.orderplatform.notification.model.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByUserId(String userId);

    List<Notification> findByUserIdAndSent(String userId, boolean sent);

    /** Looks up an earlier notification for the same delivery, see {@link Notification#getEventId()}. */
    Optional<Notification> findByEventId(String eventId);

    /**
     * Notifications whose next delivery attempt is due.
     *
     * <p>Both conditions matter: {@code sent=false} keeps delivered ones out, and the comparison
     * keeps out the ones waiting for a later attempt as well as the ones that gave up, whose
     * {@code nextAttemptAt} is null.
     */
    List<Notification> findTop50BySentFalseAndNextAttemptAtBefore(LocalDateTime now);
}
