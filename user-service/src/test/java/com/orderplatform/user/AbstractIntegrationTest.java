package com.orderplatform.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired
    protected org.springframework.test.web.servlet.MockMvc mockMvc;

    @Test
    void contextLoads() {
    }
}
