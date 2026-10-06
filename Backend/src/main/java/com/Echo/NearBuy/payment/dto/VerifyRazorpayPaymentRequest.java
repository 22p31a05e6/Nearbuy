package com.Echo.NearBuy.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyRazorpayPaymentRequest(
        @NotBlank String razorpayPaymentId,
        @NotBlank String razorpaySignature) {}
