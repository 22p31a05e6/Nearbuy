package com.Echo.NearBuy.payment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class RazorpayGatewayService {
    private final String keyId;
    private final String keySecret;
    private final RestClient restClient;

    public RazorpayGatewayService(
            @Value("${app.razorpay.key-id:}") String keyId,
            @Value("${app.razorpay.key-secret:}") String keySecret) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.razorpay.com/v1")
                .defaultHeaders(headers -> headers.setBasicAuth(keyId, keySecret))
                .build();
    }

    public GatewayOrder createOrder(Long paymentId, BigDecimal amount) {
        requireConfigured();
        long amountInPaise = amount
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact();
        if (amountInPaise <= 0) {
            throw new IllegalArgumentException("Razorpay requires a positive order amount");
        }

        try {
            GatewayOrder gatewayOrder = restClient.post()
                    .uri("/orders")
                    .body(Map.of(
                            "amount", amountInPaise,
                            "currency", "INR",
                            "receipt", "nearbuy-payment-" + paymentId))
                    .retrieve()
                    .body(GatewayOrder.class);
            if (gatewayOrder == null || gatewayOrder.id() == null || gatewayOrder.id().isBlank()) {
                throw new IllegalStateException("Razorpay returned an invalid order response");
            }
            if (!"INR".equals(gatewayOrder.currency())
                    || gatewayOrder.amount() == null
                    || gatewayOrder.amount() != amountInPaise) {
                throw new IllegalStateException(
                        "Razorpay returned an order with an unexpected amount or currency");
            }
            return gatewayOrder;
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException(
                    "Razorpay order creation failed with HTTP status "
                            + exception.getStatusCode().value(),
                    exception);
        }
    }

    public void verifyPayment(
            String gatewayOrderId,
            String paymentId,
            String signature,
            BigDecimal expectedAmount) {
        requireConfigured();
        String expectedSignature = generateSignature(gatewayOrderId, paymentId);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Invalid Razorpay payment signature");
        }

        try {
            GatewayPayment gatewayPayment = restClient.get()
                    .uri("/payments/{paymentId}", paymentId)
                    .retrieve()
                    .body(GatewayPayment.class);
            long expectedAmountInPaise = expectedAmount
                    .setScale(2, RoundingMode.UNNECESSARY)
                    .movePointRight(2)
                    .longValueExact();
            if (gatewayPayment == null
                    || !gatewayOrderId.equals(gatewayPayment.order_id())
                    || gatewayPayment.amount() == null
                    || gatewayPayment.amount() != expectedAmountInPaise
                    || !"INR".equals(gatewayPayment.currency())
                    || !"captured".equals(gatewayPayment.status())) {
                throw new IllegalStateException(
                        "Razorpay payment does not match the order or has not been captured");
            }
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException(
                    "Razorpay payment lookup failed with HTTP status "
                            + exception.getStatusCode().value(),
                    exception);
        }
    }

    public String getKeyId() {
        requireConfigured();
        return keyId;
    }

    private String generateSignature(String gatewayOrderId, String paymentId) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = hmac.doFinal(
                    (gatewayOrderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.InvalidKeyException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to verify Razorpay payment signature", exception);
        }
    }

    private void requireConfigured() {
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            throw new IllegalStateException(
                    "Razorpay is not configured; set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET");
        }
    }

    public record GatewayOrder(String id, Long amount, String currency, String status) {}

    private record GatewayPayment(
            String id,
            String order_id,
            Long amount,
            String currency,
            String status) {}
}
