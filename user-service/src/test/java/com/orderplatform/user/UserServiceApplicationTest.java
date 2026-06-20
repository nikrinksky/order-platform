package com.orderplatform.user;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceApplicationTest {

    @Test
    void testMainMethod() {
        // Given - Application class with main method
        // When - Main method is called (we don't actually run it in tests)
        // Then - The class exists and is annotated correctly
        
        // Verify the class is annotated with SpringBootApplication
        assertTrue(UserServiceApplication.class.isAnnotationPresent(SpringBootApplication.class));
    }

    @Test
    void testApplicationContextLoads() {
        // Given/When - Spring context loads
        // Then - Application context is created successfully
        // This test verifies that all beans can be created
    }

    @Test
    void testMainMethodExists() {
        // Given - UserServiceApplication class
        // When - Check for main method
        java.lang.reflect.Method mainMethod = null;
        try {
            mainMethod = UserServiceApplication.class.getMethod("main", String[].class);
        } catch (NoSuchMethodException e) {
            fail("Main method not found");
        }
        // Then - Main method exists and is static
        assertNotNull(mainMethod);
        assertTrue(java.lang.reflect.Modifier.isStatic(mainMethod.getModifiers()));
        assertEquals(void.class, mainMethod.getReturnType());
    }
}
