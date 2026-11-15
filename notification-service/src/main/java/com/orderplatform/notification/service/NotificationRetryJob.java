package com.orderplatform.notification.service;

import com.orderplatform.notification.model.Notification;
import com.orderplatform.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Retries the notifications that could not be handed to the mail server.
 *
 * <p>Without it a failed delivery was final: the record stayed {@code sent=false} and nothing ever
 * looked at it again. The consumer cannot be the retry mechanism either - its retries are bounded
 * and end on the dead letter topic, which is the right place for an unreadable record but the
 * wrong place for a mail server that was briefly down.
 *
 * <p>Each pass takes the due notifications and attempts them one by one. A failure only reschedules
 * the record with a longer delay, so one bad address cannot hold up the rest.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "notification.retry.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationRetryJob {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    /** Attempts every notification whose next attempt is due. */
    @Scheduled(fixedDelayString = "${notification.retry.interval-ms:60000}")
    public void retryUnsent() {
        List<Notification> due =
                notificationRepository.findTop50BySentFalseAndNextAttemptAtBefore(LocalDateTime.now());
        if (due.isEmpty()) {
            return;
        }
        log.info("Retrying delivery of {} notification(s)", due.size());
        for (Notification notification : due) {
            try {
                notificationService.deliver(notification);
            } catch (RuntimeException e) {
                // deliver records its own failures; this only keeps one broken record from
                // stopping the rest of the batch
                log.error("Retry of notification {} failed unexpectedly", notification.getId(), e);
            }
        }
    }
}
