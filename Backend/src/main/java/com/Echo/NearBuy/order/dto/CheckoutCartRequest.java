package com.Echo.NearBuy.order.dto;

import com.Echo.NearBuy.common.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CheckoutCartRequest(
        @NotBlank @Size(max = 1000) String deliveryAddress,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @NotNull @DecimalMin("0.00") BigDecimal deliveryFee,
        @NotNull PaymentMethod paymentMethod) {}
