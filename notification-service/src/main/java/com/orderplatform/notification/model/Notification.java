package com.orderplatform.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notifications")
@CompoundIndex(name = "ux_notification_event_id", def = "{'eventId': 1}", unique = true)
public class Notification {

    @Id
    private String id;

    private String userId;

    /**
     * Delivery id of the Kafka event that produced this notification. Delivery is at-least-once,
     * so the same event can arrive twice; the unique index keeps the second copy out.
     */
    @Indexed
    private String eventId;

    private String type;

    private String subject;

    private String body;

    private String channel;

    private boolean sent;

    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    /**
     * How many delivery attempts have been made so far. A notification that could not be handed to
     * the mail server keeps its record and is retried, so the count is what eventually stops it.
     */
    private int attempts;

    /** Why the last attempt failed; cleared once the message goes out. */
    private String lastError;

    /**
     * When the next attempt is due. {@code null} means nothing is scheduled: the notification was
     * either delivered, or the attempts ran out and it is left unsent for inspection.
     */
    private LocalDateTime nextAttemptAt;
}
