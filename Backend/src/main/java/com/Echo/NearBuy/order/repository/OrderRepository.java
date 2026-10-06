package com.Echo.NearBuy.order.repository;

import com.Echo.NearBuy.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {}
