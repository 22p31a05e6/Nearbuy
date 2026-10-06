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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(authenticatedUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @PostMapping("/orders/checkout")
    public ResponseEntity<OrderResponse> checkoutCart(
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody CheckoutCartRequest request) {
        Order order = orderService.checkoutCart(authenticatedUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @GetMapping("/orders/my-orders")
    public List<OrderResponse> getMyOrders(@AuthenticationPrincipal User authenticatedUser) {
        return orderService.getMyOrders(authenticatedUser.getId()).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @GetMapping("/orders/{id}")
    public OrderResponse getOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal User authenticatedUser) {
        return OrderResponse.from(orderService.getOrder(authenticatedUser.getId(), id));
    }

    @GetMapping("/shop/orders")
    public List<OrderResponse> getShopOrders(@AuthenticationPrincipal User authenticatedUser) {
        return orderService.getShopOrders(authenticatedUser.getId()).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @PostMapping("/orders/{orderId}/accept")
    public OrderResponse acceptPaidOnlineOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User authenticatedUser) {
        return OrderResponse.from(
                orderService.acceptPaidOnlineOrder(authenticatedUser.getId(), orderId));
    }

    @PostMapping("/orders/{orderId}/reject")
    public OrderResponse rejectOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User authenticatedUser) {
        return OrderResponse.from(orderService.rejectOrder(authenticatedUser.getId(), orderId));
    }

    @PostMapping("/orders/{orderId}/packing")
    public OrderResponse startPacking(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User authenticatedUser) {
        return OrderResponse.from(orderService.startPacking(authenticatedUser.getId(), orderId));
    }

    @PostMapping("/orders/{orderId}/packed")
    public OrderResponse markPacked(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User authenticatedUser) {
        return OrderResponse.from(orderService.markPacked(authenticatedUser.getId(), orderId));
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
