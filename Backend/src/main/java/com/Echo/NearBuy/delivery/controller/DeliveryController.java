package com.Echo.NearBuy.delivery.controller;

import com.Echo.NearBuy.delivery.dto.AvailabilityRequest;
import com.Echo.NearBuy.delivery.dto.DeliveryLocationRequest;
import com.Echo.NearBuy.delivery.dto.IncreaseDeliveryOfferRequest;
import com.Echo.NearBuy.delivery.dto.DeliveryProfileRequest;
import com.Echo.NearBuy.delivery.dto.DeliveryRangeRequest;
import com.Echo.NearBuy.delivery.dto.DeliveryRequestResponse;
import com.Echo.NearBuy.delivery.entity.DeliveryAssignment;
import com.Echo.NearBuy.delivery.entity.DeliveryPerson;
import com.Echo.NearBuy.delivery.service.DeliveryAssignmentService;
import com.Echo.NearBuy.delivery.service.DeliveryService;
import com.Echo.NearBuy.delivery.service.DeliveryOfferService;
import com.Echo.NearBuy.payment.entity.Payment;
import com.Echo.NearBuy.user.entity.User;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/delivery")
public class DeliveryController {
    private final DeliveryService deliveryService;
    private final DeliveryAssignmentService assignmentService;
    private final DeliveryOfferService deliveryOfferService;

    public DeliveryController(
            DeliveryService deliveryService,
            DeliveryAssignmentService assignmentService,
            DeliveryOfferService deliveryOfferService) {
        this.deliveryService = deliveryService;
        this.assignmentService = assignmentService;
        this.deliveryOfferService = deliveryOfferService;
    }

    @PostMapping("/profile")
    public ResponseEntity<DeliveryPersonResponse> register(
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody DeliveryProfileRequest request) {
        DeliveryPerson person = deliveryService.register(authenticatedUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(DeliveryPersonResponse.from(person));
    }

    @GetMapping("/admin/pending")
    public List<DeliveryPersonResponse> pendingProfiles(
            @AuthenticationPrincipal User authenticatedUser) {
        return deliveryService.getPendingProfiles(authenticatedUser.getId()).stream()
                .map(DeliveryPersonResponse::from)
                .toList();
    }

    @PostMapping("/admin/{deliveryPersonId}/approve")
    public DeliveryPersonResponse approve(
            @PathVariable Long deliveryPersonId,
            @AuthenticationPrincipal User authenticatedUser) {
        return DeliveryPersonResponse.from(
                deliveryService.approve(authenticatedUser.getId(), deliveryPersonId));
    }

    @PostMapping("/admin/{deliveryPersonId}/reject")
    public DeliveryPersonResponse rejectProfile(
            @PathVariable Long deliveryPersonId,
            @AuthenticationPrincipal User authenticatedUser) {
        return DeliveryPersonResponse.from(
                deliveryService.reject(authenticatedUser.getId(), deliveryPersonId));
    }

    @PatchMapping("/availability")
    public DeliveryPersonResponse availability(
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody AvailabilityRequest request) {
        return DeliveryPersonResponse.from(
                deliveryService.setAvailability(authenticatedUser.getId(), request.availabilityStatus()));
    }

    @PatchMapping("/range")
    public DeliveryPersonResponse range(
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody DeliveryRangeRequest request) {
        return DeliveryPersonResponse.from(
                deliveryService.updateRange(authenticatedUser.getId(), request.maxDeliveryRangeKm()));
    }

    @PatchMapping("/location")
    public DeliveryPersonResponse location(
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody DeliveryLocationRequest request) {
        return DeliveryPersonResponse.from(
                deliveryService.updateLocation(authenticatedUser.getId(), request));
    }

    @GetMapping("/available-orders")
    public List<DeliveryRequestResponse> availableOrders(
            @AuthenticationPrincipal User authenticatedUser) {
        return assignmentService.getAvailableOrders(authenticatedUser.getId());
    }

    @PostMapping("/orders/{orderId}/accept")
    public DeliveryAssignmentResponse accept(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User authenticatedUser) {
        return DeliveryAssignmentResponse.from(
                assignmentService.acceptOrder(authenticatedUser.getId(), orderId));
    }

    @PostMapping("/orders/{orderId}/reject")
    public DeliveryAssignmentResponse reject(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User authenticatedUser) {
        return DeliveryAssignmentResponse.from(
                assignmentService.rejectOrder(authenticatedUser.getId(), orderId));
    }

    @PostMapping("/orders/{orderId}/picked-up")
    public DeliveryAssignmentResponse pickedUp(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User authenticatedUser) {
        return DeliveryAssignmentResponse.from(
                assignmentService.markPickedUp(authenticatedUser.getId(), orderId));
    }

    @PostMapping("/orders/{orderId}/delivered")
    public DeliveryAssignmentResponse delivered(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User authenticatedUser) {
        return DeliveryAssignmentResponse.from(
                assignmentService.markDelivered(authenticatedUser.getId(), orderId));
    }

    @PostMapping("/customer/orders/{orderId}/offer-increase")
    public ResponseEntity<DeliveryOfferPaymentResponse> increaseOffer(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody IncreaseDeliveryOfferRequest request) {
        Payment payment = deliveryOfferService.increaseOffer(
                authenticatedUser.getId(), orderId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DeliveryOfferPaymentResponse.from(payment));
    }

    public record DeliveryPersonResponse(
            Long id,
            Long userId,
            String vehicleType,
            String vehicleNumber,
            String verificationStatus,
            String availabilityStatus,
            BigDecimal maxDeliveryRangeKm,
            BigDecimal currentLatitude,
            BigDecimal currentLongitude) {
        private static DeliveryPersonResponse from(DeliveryPerson person) {
            return new DeliveryPersonResponse(
                    person.getId(),
                    person.getUserId(),
                    person.getVehicleType(),
                    person.getVehicleNumber(),
                    person.getVerificationStatus().name(),
                    person.getAvailabilityStatus().name(),
                    person.getMaxDeliveryRangeKm(),
                    person.getCurrentLatitude(),
                    person.getCurrentLongitude());
        }
    }

    public record DeliveryAssignmentResponse(
            Long id,
            Long orderId,
            Long deliveryPersonId,
            String status,
            LocalDateTime assignedAt,
            LocalDateTime pickedUpAt,
            LocalDateTime deliveredAt) {
        private static DeliveryAssignmentResponse from(DeliveryAssignment assignment) {
            return new DeliveryAssignmentResponse(
                    assignment.getId(),
                    assignment.getOrderId(),
                    assignment.getDeliveryPersonId(),
                    assignment.getStatus().name(),
                    assignment.getAssignedAt(),
                    assignment.getPickedUpAt(),
                    assignment.getDeliveredAt());
        }
    }

    public record DeliveryOfferPaymentResponse(
            Long orderId,
            Long paymentId,
            BigDecimal amountToPay,
            String paymentStatus) {
        private static DeliveryOfferPaymentResponse from(Payment payment) {
            return new DeliveryOfferPaymentResponse(
                    payment.getOrderId(),
                    payment.getId(),
                    payment.getAmount(),
                    payment.getPaymentStatus().name());
        }
    }
}
