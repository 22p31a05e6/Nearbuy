package com.Echo.NearBuy.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductRequest(
        @NotNull Long shopId,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 100) String category,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @NotBlank @Size(max = 50) String unit,
        @NotNull @Min(0) Integer quantity,
        @Size(max = 1000) String imageUrl,
        Boolean active) {
    public boolean isActive() {
        return active == null || active;
    }
}
