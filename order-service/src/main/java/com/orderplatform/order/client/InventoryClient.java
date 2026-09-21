package com.orderplatform.order.client;

/**
 * Абстракция инвентарь-сервиса для order-service.
 *
 * <p>Назначение — отделить бизнес-логику заказа от HTTP-деталей и сделать
 * операции с остатками тестируемыми без URL-паттернов. Реализация по
 * умолчанию — {@link RestTemplateInventoryClient}.</p>
 */
public interface InventoryClient {

    /**
     * Проверяет достаточность доступного остатка на складе.
     *
     * <p>Fail-open: при недоступности inventory-service возвращает true —
     * резервирование (reserve) остаётся единственной гарантией, и падение
     * inventory не должно блокировать оформление заказов.</p>
     *
     * @param productId идентификатор товара
     * @param quantity  запрашиваемое количество
     * @return true если остаток достаточен (или проверка недоступна)
     */
    boolean hasStock(String productId, int quantity);

    /**
     * Резервирует указанный объём товара под заказ.
     *
     * @param productId идентификатор товара
     * @param quantity  резервируемое количество
     * @param orderId   идентификатор заказа
     * @return true если резервирование выполнено
     */
    boolean reserve(String productId, int quantity, String orderId);

    /**
     * Освобождает ранее зарезервированный объём товара.
     *
     * @param productId идентификатор товара
     * @param quantity  освобождаемое количество
     * @param orderId   идентификатор заказа
     */
    void release(String productId, int quantity, String orderId);
}
