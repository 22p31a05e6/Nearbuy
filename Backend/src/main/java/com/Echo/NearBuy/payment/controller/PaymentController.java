package com.Echo.NearBuy.payment.controller;

import com.Echo.NearBuy.payment.dto.RazorpayCheckoutResponse;
import com.Echo.NearBuy.payment.dto.VerifyRazorpayPaymentRequest;
import com.Echo.NearBuy.payment.entity.Payment;
import com.Echo.NearBuy.payment.service.PaymentService;
import com.Echo.NearBuy.user.entity.User;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/orders/{orderId}")
    public PaymentResponse getPayment(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User authenticatedUser) {
        return PaymentResponse.from(
                paymentService.getCustomerPayment(authenticatedUser.getId(), orderId));
    }

    @PostMapping("/orders/{orderId}/razorpay/order")
    public RazorpayCheckoutResponse createRazorpayOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User authenticatedUser) {
        return paymentService.createRazorpayOrder(authenticatedUser.getId(), orderId);
    }

    @PostMapping("/orders/{orderId}/razorpay/verify")
    public PaymentResponse verifyRazorpayPayment(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody VerifyRazorpayPaymentRequest request) {
        return PaymentResponse.from(
                paymentService.verifyRazorpayPayment(authenticatedUser.getId(), orderId, request));
    }

    @PostMapping("/orders/{orderId}/cod/collect")
    public PaymentResponse collectCashOnDelivery(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User authenticatedUser) {
        return PaymentResponse.from(
                paymentService.collectCashOnDelivery(authenticatedUser.getId(), orderId));
    }

    public record PaymentResponse(
            Long id,
            Long orderId,
            BigDecimal amount,
            String paymentMethod,
            String paymentStatus,
            String transactionId,
            LocalDateTime createdAt) {
        private static PaymentResponse from(Payment payment) {
            return new PaymentResponse(
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getAmount(),
                    payment.getPaymentMethod().name(),
                    payment.getPaymentStatus().name(),
                    payment.getTransactionId(),
                    payment.getCreatedAt());
        }
    }
}
