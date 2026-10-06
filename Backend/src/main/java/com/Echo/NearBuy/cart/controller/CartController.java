package com.Echo.NearBuy.cart.controller;

import com.Echo.NearBuy.cart.dto.AddCartItemRequest;
import com.Echo.NearBuy.cart.entity.Cart;
import com.Echo.NearBuy.cart.entity.CartItem;
import com.Echo.NearBuy.cart.service.CartService;
import com.Echo.NearBuy.user.entity.User;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/items")
    public ResponseEntity<CartItemResponse> addItem(
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody AddCartItemRequest request) {
        CartItem item = cartService.addItem(authenticatedUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CartItemResponse.from(item));
    }

    @GetMapping
    public CartResponse getCart(@AuthenticationPrincipal User authenticatedUser) {
        Cart cart = cartService.getCart(authenticatedUser.getId());
        List<CartItemResponse> items = cartService.getItems(authenticatedUser.getId()).stream()
                .map(CartItemResponse::from)
                .toList();
        return new CartResponse(cart.getId(), cart.getCustomerId(), cart.getShopId(), items);
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> removeItem(
            @PathVariable Long itemId,
            @AuthenticationPrincipal User authenticatedUser) {
        cartService.removeItem(authenticatedUser.getId(), itemId);
        return ResponseEntity.noContent().build();
    }

    public record CartResponse(Long id, Long customerId, Long shopId, List<CartItemResponse> items) {}

    public record CartItemResponse(
            Long id,
            Long cartId,
            Long productUnitId,
            Integer quantity,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        private static CartItemResponse from(CartItem item) {
            return new CartItemResponse(
                    item.getId(),
                    item.getCartId(),
                    item.getProductUnitId(),
                    item.getQuantity(),
                    item.getCreatedAt(),
                    item.getUpdatedAt());
        }
    }
}
