package com.orderplatform.order.repository;

import com.orderplatform.order.model.ItemReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemReservationRepository extends JpaRepository<ItemReservation, String> {

    Optional<ItemReservation> findByEventId(String eventId);

    List<ItemReservation> findByOrderIdAndProductId(String orderId, String productId);
}
