package com.orderplatform.notification.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.notification.model.Notification;
import com.orderplatform.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration тест для Notification Controller.
 */
@AutoConfigureMockMvc
class NotificationControllerIntegrationTest extends AbstractNotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    @Test
    void shouldCreateNotification() throws Exception {
        String notificationJson = """
                {
                    "userId": "user-123",
                    "type": "ORDER_CREATED",
                    "message": "Your order has been created",
                    "email": "test@example.com"
                }
                """;

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notificationJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("user-123"))
                .andExpect(jsonPath("$.type").value("ORDER_CREATED"));
    }

    @Test
    void shouldRejectNotificationWithoutEmail() throws Exception {
        String notificationJson = """
                {
                    "userId": "user-no-email",
                    "type": "ORDER_CREATED",
                    "message": "Test message"
                }
                """;

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notificationJson))
                .andExpect(status().isBadRequest());
    }
}
