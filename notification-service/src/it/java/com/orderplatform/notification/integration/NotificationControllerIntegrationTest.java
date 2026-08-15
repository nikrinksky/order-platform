package com.orderplatform.notification.integration;

import com.orderplatform.notification.AbstractNotificationIntegrationTest;
import com.orderplatform.notification.model.Notification;
import com.orderplatform.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for NotificationController.
 */
@AutoConfigureMockMvc
class NotificationControllerIntegrationTest extends AbstractNotificationIntegrationTest {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    @Test
    void shouldGetNotificationsByUser() throws Exception {
        notificationRepository.save(Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .type("ORDER_CREATED")
                .subject("Order Created")
                .body("Your order has been created")
                .channel("EMAIL")
                .sent(false)
                .build());

        mockMvc.perform(get("/api/notifications/user/user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("user-1"))
                .andExpect(jsonPath("$[0].type").value("ORDER_CREATED"));
    }

    @Test
    void shouldGetUnsentNotifications() throws Exception {
        notificationRepository.save(Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .type("ORDER_CREATED")
                .sent(false)
                .build());
        notificationRepository.save(Notification.builder()
                .id("notif-2")
                .userId("user-1")
                .type("ORDER_STATUS_CHANGED")
                .sent(true)
                .build());

        mockMvc.perform(get("/api/notifications/user/user-1/unsent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sent").value(false));
    }

    @Test
    void shouldMarkAsSent() throws Exception {
        notificationRepository.save(Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .type("ORDER_CREATED")
                .sent(false)
                .build());

        mockMvc.perform(patch("/api/notifications/notif-1/mark-sent"))
                .andExpect(status().isOk());

        Notification updated = notificationRepository.findById("notif-1").orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(updated.isSent());
        org.junit.jupiter.api.Assertions.assertNotNull(updated.getSentAt());
    }
}