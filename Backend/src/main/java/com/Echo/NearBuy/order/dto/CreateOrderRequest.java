package com.Echo.NearBuy.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.Echo.NearBuy.common.enums.PaymentMethod;
import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        @NotNull Long shopId,
        @NotEmpty List<@Valid OrderItemRequest> items,
        @NotBlank @Size(max = 1000) String deliveryAddress,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @NotNull @DecimalMin("0.00") BigDecimal deliveryFee,
        @NotNull PaymentMethod paymentMethod) {

    public record OrderItemRequest(
            @NotNull Long productUnitId,
            @NotNull @Positive Integer quantity) {}
}
