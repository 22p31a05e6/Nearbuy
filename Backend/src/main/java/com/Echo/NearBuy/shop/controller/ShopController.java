package com.Echo.NearBuy.shop.controller;

import com.Echo.NearBuy.shop.dto.CreateShopRequest;
import com.Echo.NearBuy.shop.entity.Shop;
import com.Echo.NearBuy.shop.service.ShopService;
import com.Echo.NearBuy.user.entity.User;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shops")
public class ShopController {
    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @PostMapping
    public ResponseEntity<ShopResponse> createShop(
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody CreateShopRequest request) {
        Shop shop = shopService.createShop(authenticatedUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ShopResponse.from(shop));
    }

    @GetMapping("/{id}")
    public ShopResponse getShop(
            @PathVariable Long id,
            @AuthenticationPrincipal User authenticatedUser) {
        return ShopResponse.from(shopService.getShop(id, authenticatedUser.getId()));
    }

    @PutMapping("/{id}")
    public ShopResponse updateShop(
            @PathVariable Long id,
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody CreateShopRequest request) {
        Shop shop = shopService.updateShop(id, authenticatedUser.getId(), request);
        return ShopResponse.from(shop);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShop(
            @PathVariable Long id,
            @AuthenticationPrincipal User authenticatedUser) {
        shopService.deleteShop(id, authenticatedUser.getId());
        return ResponseEntity.noContent().build();
    }

    public record ShopResponse(
            Long id,
            Long ownerId,
            String shopName,
            String description,
            String phone,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        private static ShopResponse from(Shop shop) {
            return new ShopResponse(
                    shop.getId(),
                    shop.getOwnerId(),
                    shop.getShopName(),
                    shop.getDescription(),
                    shop.getPhone(),
                    shop.getAddress(),
                    shop.getLatitude(),
                    shop.getLongitude(),
                    shop.getStatus(),
                    shop.getCreatedAt(),
                    shop.getUpdatedAt());
        }
    }
}
