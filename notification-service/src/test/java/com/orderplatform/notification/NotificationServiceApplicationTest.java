package com.orderplatform.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class NotificationServiceApplicationTest {

    @Test
    void contextLoads() {
    }

    @Test
    void shouldHaveSpringBootApplicationAnnotation() {
        assertTrue(NotificationServiceApplication.class.isAnnotationPresent(SpringBootApplication.class));
    }

    @Test
    void shouldHaveMainMethod() throws NoSuchMethodException {
        Method mainMethod = NotificationServiceApplication.class.getMethod("main", String[].class);

        assertTrue(Modifier.isStatic(mainMethod.getModifiers()));
        assertEquals(void.class, mainMethod.getReturnType());
    }
}
