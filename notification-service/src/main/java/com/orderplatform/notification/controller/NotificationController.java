package com.orderplatform.notification.controller;

import com.orderplatform.notification.model.Notification;
import com.orderplatform.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ROLE_ADMIN') or #userId == authentication.name")
    public ResponseEntity<List<Notification>> getNotificationsByUser(@PathVariable("userId") String userId) {
        return ResponseEntity.ok(notificationService.getNotificationsByUserId(userId));
    }

    @GetMapping("/user/{userId}/unsent")
    @PreAuthorize("hasRole('ROLE_ADMIN') or #userId == authentication.name")
    public ResponseEntity<List<Notification>> getUnsentNotifications(@PathVariable("userId") String userId) {
        return ResponseEntity.ok(notificationService.getUnsentNotificationsByUserId(userId));
    }

    @PatchMapping("/{id}/mark-sent")
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_MANAGER')")
    public ResponseEntity<Void> markAsSent(@PathVariable("id") String id) {
        notificationService.markAsSent(id);
        return ResponseEntity.ok().build();
    }
}
