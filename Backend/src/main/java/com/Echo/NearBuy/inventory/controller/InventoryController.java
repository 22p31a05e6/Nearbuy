package com.Echo.NearBuy.inventory.controller;

import com.Echo.NearBuy.inventory.entity.Inventory;
import com.Echo.NearBuy.inventory.service.InventoryService;
import com.Echo.NearBuy.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping
    public ResponseEntity<InventoryResponse> createInventory(
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody CreateInventoryRequest request) {
        Inventory inventory = inventoryService.createInventory(
                authenticatedUser.getId(),
                request.shopId(),
                request.productUnitId(),
                request.availableQuantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(InventoryResponse.from(inventory));
    }

    @GetMapping("/shops/{shopId}")
    public List<InventoryResponse> getShopInventory(
            @PathVariable Long shopId,
            @AuthenticationPrincipal User authenticatedUser) {
        return inventoryService.getShopInventory(authenticatedUser.getId(), shopId).stream()
                .map(InventoryResponse::from)
                .toList();
    }

    @PostMapping("/{inventoryId}/reserve")
    public InventoryResponse reserve(
            @PathVariable Long inventoryId,
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody QuantityRequest request) {
        return InventoryResponse.from(
                inventoryService.reserve(authenticatedUser.getId(), inventoryId, request.quantity()));
    }

    @PostMapping("/{inventoryId}/commit")
    public InventoryResponse commitReservation(
            @PathVariable Long inventoryId,
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody QuantityRequest request) {
        return InventoryResponse.from(
                inventoryService.commitReservation(authenticatedUser.getId(), inventoryId, request.quantity()));
    }

    @PostMapping("/{inventoryId}/release")
    public InventoryResponse releaseReservation(
            @PathVariable Long inventoryId,
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody QuantityRequest request) {
        return InventoryResponse.from(
                inventoryService.releaseReservation(authenticatedUser.getId(), inventoryId, request.quantity()));
    }

    public record CreateInventoryRequest(
            @NotNull Long shopId,
            @NotNull Long productUnitId,
            @NotNull @Min(0) Integer availableQuantity) {}

    public record QuantityRequest(@NotNull @Min(1) Integer quantity) {}

    public record InventoryResponse(
            Long id,
            Long shopId,
            Long productUnitId,
            Integer availableQuantity,
            Integer reservedQuantity,
            LocalDateTime updatedAt) {
        private static InventoryResponse from(Inventory inventory) {
            return new InventoryResponse(
                    inventory.getId(),
                    inventory.getShopId(),
                    inventory.getProductUnitId(),
                    inventory.getAvailableQuantity(),
                    inventory.getReservedQuantity(),
                    inventory.getUpdatedAt());
        }
    }
}
