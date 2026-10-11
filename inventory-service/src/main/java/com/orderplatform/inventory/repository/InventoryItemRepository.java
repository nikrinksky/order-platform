package com.orderplatform.inventory.repository;

import com.orderplatform.inventory.model.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, String> {

    Optional<InventoryItem> findByProductId(String productId);

    /**
     * Reads the item and locks its row until the surrounding transaction ends
     * ({@code SELECT ... FOR UPDATE}).
     *
     * <p>Every mutation of the stock must go through this method. The reservation logic is a
     * read-modify-write, so without the lock two concurrent reservations both read the same
     * free quantity and both succeed: with a stock of 5, 40 parallel reservations used to pass
     * 38-40 of them. The lock serialises them, so each one sees the result of the previous.
     *
     * @param productId the product whose row is locked
     * @return the item, if present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.productId = :productId")
    Optional<InventoryItem> findByProductIdForUpdate(@Param("productId") String productId);
}
