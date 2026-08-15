package com.orderplatform.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceApplicationTest {

    @Test
    void contextLoads() {
    }

    @Test
    void shouldHaveSpringBootApplicationAnnotation() {
        assertTrue(OrderServiceApplication.class.isAnnotationPresent(SpringBootApplication.class));
    }

    @Test
    void shouldHaveMainMethod() throws NoSuchMethodException {
        Method mainMethod = OrderServiceApplication.class.getMethod("main", String[].class);

        assertTrue(Modifier.isStatic(mainMethod.getModifiers()));
        assertEquals(void.class, mainMethod.getReturnType());
    }
}
