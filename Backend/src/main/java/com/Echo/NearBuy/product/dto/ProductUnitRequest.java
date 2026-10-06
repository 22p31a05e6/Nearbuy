package com.Echo.NearBuy.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ProductUnitRequest(
        @NotBlank String unitType,
        @NotNull @DecimalMin("0.00") BigDecimal unitValue,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @NotNull @Min(0) Integer stockQuantity) {}
