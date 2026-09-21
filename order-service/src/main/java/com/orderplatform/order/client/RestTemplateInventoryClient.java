package com.orderplatform.order.client;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Реализация {@link InventoryClient} поверх RestTemplate.
 *
 * <p>Единая точка формирования URL inventory-service. При недоступности
 * сервиса или ошибке HTTP логика fail-open: проверка остатка считается
 * пройденной, а reserve — неудачным (чтобы заказ не остался без резерва).</p>
 */
@Component
public class RestTemplateInventoryClient implements InventoryClient {

    private final RestTemplate restTemplate;
    private final String inventoryBaseUrl;

    public RestTemplateInventoryClient(
            RestTemplate restTemplate,
            @Value("${services.inventory-service.url}") String inventoryBaseUrl) {
        this.restTemplate = restTemplate;
        this.inventoryBaseUrl = inventoryBaseUrl;
    }

    @Override
    public boolean hasStock(String productId, int quantity) {
        try {
            String url = UriComponentsBuilder.fromUriString(inventoryBaseUrl)
                    .path("/api/inventory/product/{productId}")
                    .buildAndExpand(productId)
                    .toUriString();

            Map<?, ?> item = restTemplate.getForObject(url, Map.class);
            if (item == null || !(item.get("availableQuantity") instanceof Number available)) {
                return true;
            }
            return available.intValue() >= quantity;
        } catch (ResourceAccessException | HttpStatusCodeException e) {
            return true;
        }
    }

    @Override
    public boolean reserve(String productId, int quantity, String orderId) {
        try {
            String url = inventoryBaseUrl + "/api/inventory/reserve";

            Map<String, Object> payload = Map.of(
                    "orderId", orderId,
                    "productId", productId,
                    "quantity", quantity);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    url, new HttpEntity<>(payload, headers), Map.class);

            return response == null || !Boolean.FALSE.equals(response.get("reserved"));
        } catch (ResourceAccessException | HttpStatusCodeException e) {
            return false;
        }
    }

    @Override
    public void release(String productId, int quantity, String orderId) {
        try {
            String url = UriComponentsBuilder.fromUriString(inventoryBaseUrl)
                    .path("/api/inventory/release")
                    .queryParam("orderId", orderId)
                    .queryParam("productId", productId)
                    .queryParam("quantity", quantity)
                    .toUriString();

            restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, Void.class);
        } catch (ResourceAccessException | HttpStatusCodeException ignored) {
            // Освобождение best-effort: заказ уже отменён, инвентарь
            // синхронизируется повторной операцией или компенсацией.
        }
    }
}
