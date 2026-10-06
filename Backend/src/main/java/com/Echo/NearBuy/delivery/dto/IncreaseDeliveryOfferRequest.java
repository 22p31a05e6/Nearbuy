package com.Echo.NearBuy.delivery.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record IncreaseDeliveryOfferRequest(
        @NotNull @DecimalMin("0.01") BigDecimal newDeliveryFee) {}
