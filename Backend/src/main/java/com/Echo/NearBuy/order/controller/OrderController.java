package com.Echo.NearBuy.order.controller;

import com.Echo.NearBuy.order.dto.CreateOrderRequest;
import com.Echo.NearBuy.order.dto.CheckoutCartRequest;
import com.Echo.NearBuy.order.entity.Order;
import com.Echo.NearBuy.order.service.OrderService;
import com.Echo.NearBuy.user.entity.User;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(authenticatedUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @PostMapping("/checkout")
    public ResponseEntity<OrderResponse> checkoutCart(
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody CheckoutCartRequest request) {
        Order order = orderService.checkoutCart(authenticatedUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    public record OrderResponse(
            Long id,
            Long customerId,
            Long shopId,
            BigDecimal totalAmount,
            BigDecimal deliveryFee,
            String status,
            String paymentStatus,
            String paymentMethod,
            String deliveryAddress,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        private static OrderResponse from(Order order) {
            return new OrderResponse(
                    order.getId(),
                    order.getCustomerId(),
                    order.getShopId(),
                    order.getTotalAmount(),
                    order.getDeliveryFee(),
                    order.getStatus(),
                    order.getPaymentStatus(),
                    order.getPaymentMethod(),
                    order.getDeliveryAddress(),
                    order.getLatitude(),
                    order.getLongitude(),
                    order.getCreatedAt(),
                    order.getUpdatedAt());
        }
    }
}
