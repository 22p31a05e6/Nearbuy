package com.Echo.NearBuy.delivery.dto;

import java.math.BigDecimal;

public record DeliveryRequestResponse(
        Long orderId,
        String shopName,
        String customerName,
        String deliveryAddress,
        BigDecimal distanceToShopKm,
        BigDecimal shopToCustomerKm,
        BigDecimal totalDistanceKm,
        BigDecimal customerOfferedPrice,
        BigDecimal estimatedEarning,
        BigDecimal earningPerKm) {}
