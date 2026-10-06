package com.Echo.NearBuy.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeliveryProfileRequest(
        @NotBlank @Size(max = 100) String vehicleType,
        @NotBlank @Size(max = 50) String vehicleNumber) {}
