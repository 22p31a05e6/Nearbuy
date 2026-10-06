package com.Echo.NearBuy.payment.repository;

import com.Echo.NearBuy.payment.entity.Payment;
import com.Echo.NearBuy.common.enums.PaymentPurpose;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findFirstByOrderIdAndPurposeOrderByCreatedAtAsc(
            Long orderId,
            PaymentPurpose purpose);

    Optional<Payment> findFirstByOrderIdAndPurposeIsNullOrderByCreatedAtAsc(Long orderId);

    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    boolean existsByOrderIdAndPurposeAndPaymentStatus(
            Long orderId,
            PaymentPurpose purpose,
            com.Echo.NearBuy.common.enums.PaymentStatus paymentStatus);
}
