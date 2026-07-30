package com.orderplatform.notification.controller;

import com.orderplatform.notification.model.Notification;
import com.orderplatform.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Notification>> getNotificationsByUser(@PathVariable String userId) {
        return ResponseEntity.ok(notificationService.getNotificationsByUserId(userId));
    }

    @GetMapping("/user/{userId}/unsent")
    public ResponseEntity<List<Notification>> getUnsentNotifications(@PathVariable String userId) {
        return ResponseEntity.ok(notificationService.getUnsentNotificationsByUserId(userId));
    }

    @PatchMapping("/{id}/mark-sent")
    public ResponseEntity<Void> markAsSent(@PathVariable String id) {
        notificationService.markAsSent(id);
        return ResponseEntity.ok().build();
    }
}
