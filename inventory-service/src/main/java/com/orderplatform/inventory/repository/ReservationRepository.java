package com.orderplatform.inventory.repository;

import com.orderplatform.inventory.model.Reservation;
import com.orderplatform.inventory.model.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, String> {

    /**
     * Finds the reservation an order holds for a product.
     *
     * <p>The pair is unique, so this is the lookup both idempotency checks are built on.
     *
     * @param orderId   the order
     * @param productId the product
     * @return the reservation, if the order ever reserved this product
     */
    Optional<Reservation> findByOrderIdAndProductId(String orderId, String productId);

    /**
     * Same lookup with the row locked until the transaction ends.
     *
     * <p>Used after the inventory row is locked, so two releases of the same order cannot both
     * read {@code ACTIVE} and subtract twice.
     *
     * @param orderId   the order
     * @param productId the product
     * @return the locked reservation, if present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.orderId = :orderId and r.productId = :productId")
    Optional<Reservation> findByOrderIdAndProductIdForUpdate(@Param("orderId") String orderId,
                                                             @Param("productId") String productId);

    /**
     * All reservations of an order in a given state.
     *
     * @param orderId the order
     * @param status  the state to filter by
     * @return matching reservations, empty when the order holds none
     */
    List<Reservation> findByOrderIdAndStatus(String orderId, ReservationStatus status);
}
