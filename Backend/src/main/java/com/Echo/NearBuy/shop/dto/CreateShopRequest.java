package com.Echo.NearBuy.shop.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateShopRequest(
        @NotBlank @Size(max = 255) String shopName,
        @Size(max = 2000) String description,
        @Size(max = 255) String phone,
        @Size(max = 1000) String address,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude) {
}
