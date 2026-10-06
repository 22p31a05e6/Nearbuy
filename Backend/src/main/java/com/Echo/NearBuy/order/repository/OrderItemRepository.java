package com.Echo.NearBuy.order.repository;

import com.Echo.NearBuy.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {}
