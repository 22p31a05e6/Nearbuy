package com.Echo.NearBuy.order.repository;

import com.Echo.NearBuy.order.entity.Order;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<Order> findByShopIdInOrderByCreatedAtDesc(List<Long> shopIds);

    List<Order> findByStatusInOrderByCreatedAtAsc(List<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    java.util.Optional<Order> findByIdForUpdate(@Param("id") Long id);
}
