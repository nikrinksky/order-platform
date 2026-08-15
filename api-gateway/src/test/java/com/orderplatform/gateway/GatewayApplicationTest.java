package com.orderplatform.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class GatewayApplicationTest {

    @Test
    void contextLoads() {
    }

    @Test
    void shouldHaveSpringBootApplicationAnnotation() {
        assertTrue(GatewayApplication.class.isAnnotationPresent(SpringBootApplication.class));
    }

    @Test
    void shouldHaveMainMethod() throws NoSuchMethodException {
        Method mainMethod = GatewayApplication.class.getMethod("main", String[].class);

        assertTrue(Modifier.isStatic(mainMethod.getModifiers()));
        assertEquals(void.class, mainMethod.getReturnType());
    }
}