package com.Echo.NearBuy.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddCartItemRequest(
        @NotNull Long productUnitId,
        @NotNull @Positive Integer quantity) {}
