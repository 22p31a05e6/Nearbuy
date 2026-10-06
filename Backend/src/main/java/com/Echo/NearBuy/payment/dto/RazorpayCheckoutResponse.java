package com.Echo.NearBuy.payment.dto;

public record RazorpayCheckoutResponse(
        Long appOrderId,
        String razorpayOrderId,
        String keyId,
        Long amount,
        String currency) {}
