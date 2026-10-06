package com.Echo.NearBuy.delivery.repository;

import com.Echo.NearBuy.delivery.entity.DeliveryAssignment;
import com.Echo.NearBuy.delivery.enums.DeliveryStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, Long> {
    Optional<DeliveryAssignment> findByOrderIdAndDeliveryPersonId(Long orderId, Long deliveryPersonId);

    Optional<DeliveryAssignment> findByOrderIdAndStatus(Long orderId, DeliveryStatus status);

    List<DeliveryAssignment> findByOrderId(Long orderId);

    boolean existsByOrderIdAndStatus(Long orderId, DeliveryStatus status);

    void deleteByOrderIdAndStatus(Long orderId, DeliveryStatus status);

    boolean existsByDeliveryPersonIdAndStatus(Long deliveryPersonId, DeliveryStatus status);
}
