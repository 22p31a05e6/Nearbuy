package com.Echo.NearBuy.offline.dto;

import com.Echo.NearBuy.offline.enums.OfflinePaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record CreateOfflineSaleRequest(
        @NotNull Long shopId,
        @NotNull OfflinePaymentMethod paymentMethod,
        @NotEmpty List<@Valid OfflineSaleItemRequest> items) {

    public record OfflineSaleItemRequest(
            @NotNull Long productUnitId,
            @NotNull @Positive Integer quantity) {}
}
