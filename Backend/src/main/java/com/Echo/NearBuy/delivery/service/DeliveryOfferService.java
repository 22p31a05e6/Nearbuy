package com.Echo.NearBuy.delivery.service;

import com.Echo.NearBuy.delivery.dto.IncreaseDeliveryOfferRequest;
import com.Echo.NearBuy.payment.entity.Payment;
import com.Echo.NearBuy.payment.service.PaymentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryOfferService {
    private final PaymentService paymentService;

    public DeliveryOfferService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Transactional
    public Payment increaseOffer(Long customerId, Long orderId, IncreaseDeliveryOfferRequest request) {
        return paymentService.createDeliveryFeeAdjustment(
                customerId, orderId, request.newDeliveryFee());
    }
}
