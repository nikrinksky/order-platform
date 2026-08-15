package com.orderplatform.product;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class ProductServiceApplicationTest {

    @Test
    void contextLoads() {
    }

    @Test
    void shouldHaveSpringBootApplicationAnnotation() {
        assertTrue(ProductServiceApplication.class.isAnnotationPresent(SpringBootApplication.class));
    }

    @Test
    void shouldHaveMainMethod() throws NoSuchMethodException {
        Method mainMethod = ProductServiceApplication.class.getMethod("main", String[].class);

        assertTrue(Modifier.isStatic(mainMethod.getModifiers()));
        assertEquals(void.class, mainMethod.getReturnType());
    }
}
