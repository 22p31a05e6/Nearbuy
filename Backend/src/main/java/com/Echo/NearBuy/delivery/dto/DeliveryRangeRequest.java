package com.Echo.NearBuy.delivery.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DeliveryRangeRequest(
        @NotNull @DecimalMin("0.1") @DecimalMax("20.0") BigDecimal maxDeliveryRangeKm) {}
