package com.orderplatform.notification.controller;

import com.orderplatform.notification.model.Notification;
import com.orderplatform.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
        })
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    private Notification sampleNotification() {
        return Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .type("ORDER_CREATED")
                .subject("Order Created")
                .sent(false)
                .build();
    }

    @Test
    void shouldGetNotificationsByUser() throws Exception {
        when(notificationService.getNotificationsByUserId("user-1")).thenReturn(List.of(sampleNotification()));

        mockMvc.perform(get("/api/notifications/user/user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("user-1"));
    }

    @Test
    void shouldGetUnsentNotifications() throws Exception {
        when(notificationService.getUnsentNotificationsByUserId("user-1"))
                .thenReturn(List.of(sampleNotification()));

        mockMvc.perform(get("/api/notifications/user/user-1/unsent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sent").value(false));
    }

    @Test
    void shouldMarkAsSent() throws Exception {
        doNothing().when(notificationService).markAsSent(anyString());

        mockMvc.perform(patch("/api/notifications/notif-1/mark-sent"))
                .andExpect(status().isOk());
    }
}