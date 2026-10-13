package com.orderplatform.order;

import com.orderplatform.order.client.InventoryClient;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestTemplate;

@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
    "spring.kafka.enabled=false",
    "spring.security.enabled=false"
})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = OrderServiceApplication.class)
@ActiveProfiles("integration-tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension.class)
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
public abstract class AbstractOrderIntegrationTest {

    @MockitoBean
    protected KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * The catalogue and the warehouse are outside the scope of these tests, and the service now
     * fails closed when it cannot price a product, so both calls are stubbed out here.
     */
    @MockitoBean
    protected InventoryClient inventoryClient;

    @MockitoBean
    protected RestTemplate restTemplate;
}
