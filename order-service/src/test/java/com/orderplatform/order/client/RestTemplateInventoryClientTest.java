package com.orderplatform.order.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RestTemplateInventoryClientTest {

    @Mock
    private RestTemplate restTemplate;

    private RestTemplateInventoryClient client() {
        return new RestTemplateInventoryClient(restTemplate, "http://inventory-service:8081");
    }

    @Test
    void hasStockReturnsTrueWhenAvailableQuantityIsEnough() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("productId", "p1", "availableQuantity", 10));

        assertThat(client().hasStock("p1", 5)).isTrue();
    }

    @Test
    void hasStockReturnsFalseWhenAvailableQuantityIsInsufficient() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("productId", "p1", "availableQuantity", 2));

        assertThat(client().hasStock("p1", 5)).isFalse();
    }

    @Test
    void hasStockFailsOpenWhenItemIsUnknown() {
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(null);

        assertThat(client().hasStock("unknown", 5)).isTrue();
    }

    @Test
    void hasStockFailsOpenWhenQuantityFieldIsMissing() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("productId", "p1"));

        assertThat(client().hasStock("p1", 5)).isTrue();
    }

    @Test
    void hasStockFailsOpenWhenInventoryIsUnreachable() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        assertThat(client().hasStock("p1", 5)).isTrue();
    }

    @Test
    void reserveReturnsTrueWhenInventoryConfirmsReservation() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of("reserved", true));

        assertThat(client().reserve("p1", 5, "order-1")).isTrue();
    }

    @Test
    void reserveReturnsFalseWhenInventoryRejectsReservation() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of("reserved", false));

        assertThat(client().reserve("p1", 5, "order-1")).isFalse();
    }

    @Test
    void reserveFailsOpenWhenInventoryReturnsNoBody() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(null);

        assertThat(client().reserve("p1", 5, "order-1")).isTrue();
    }

    @Test
    void reserveReturnsFalseWhenInventoryIsUnreachable() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        assertThat(client().reserve("p1", 5, "order-1")).isFalse();
    }

    @Test
    void releaseCallsInventoryEndpoint() {
        client().release("p1", 5, "order-1");

        verify(restTemplate).exchange(anyString(), any(), any(), eq(Void.class));
    }

    @Test
    void releaseSwallowsInventoryErrors() {
        when(restTemplate.exchange(anyString(), any(), any(), eq(Void.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        assertThatCode(() -> client().release("p1", 5, "order-1")).doesNotThrowAnyException();
    }
}
