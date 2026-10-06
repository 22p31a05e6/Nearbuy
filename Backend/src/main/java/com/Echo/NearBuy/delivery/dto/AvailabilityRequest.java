package com.Echo.NearBuy.delivery.dto;

import com.Echo.NearBuy.delivery.enums.AvailabilityStatus;
import jakarta.validation.constraints.NotNull;

public record AvailabilityRequest(@NotNull AvailabilityStatus availabilityStatus) {}
