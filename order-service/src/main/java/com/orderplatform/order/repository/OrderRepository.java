package com.orderplatform.order.repository;

import com.orderplatform.order.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByUserId(String userId);
    Optional<Order> findByOrderNumber(String orderNumber);
}
