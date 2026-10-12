package com.orderplatform.order.repository;

import com.orderplatform.order.model.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByUserId(String userId);
    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * Reads the order and locks its row until the surrounding transaction ends
     * ({@code SELECT ... FOR UPDATE}).
     *
     * <p>State changes must go through this method. Without the lock two concurrent reserve
     * requests both read the order as NEW, both reserve the stock and the order is reserved twice;
     * the lock serialises them so the second one sees the status the first one wrote.
     *
     * @param id order id
     * @return the order, if present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") String id);
}
