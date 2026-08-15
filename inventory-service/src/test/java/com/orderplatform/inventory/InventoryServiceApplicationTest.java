package com.orderplatform.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class InventoryServiceApplicationTest {

    @Test
    void contextLoads() {
    }

    @Test
    void shouldHaveSpringBootApplicationAnnotation() {
        assertTrue(InventoryServiceApplication.class.isAnnotationPresent(SpringBootApplication.class));
    }

    @Test
    void shouldHaveMainMethod() throws NoSuchMethodException {
        Method mainMethod = InventoryServiceApplication.class.getMethod("main", String[].class);

        assertTrue(Modifier.isStatic(mainMethod.getModifiers()));
        assertEquals(void.class, mainMethod.getReturnType());
    }
}
