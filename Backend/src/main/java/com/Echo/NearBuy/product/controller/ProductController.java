package com.Echo.NearBuy.product.controller;

import com.Echo.NearBuy.product.dto.ProductRequest;
import com.Echo.NearBuy.product.entity.Product;
import com.Echo.NearBuy.product.service.ProductService;
import com.Echo.NearBuy.user.entity.User;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody ProductRequest request) {
        Product product = productService.createProduct(authenticatedUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.from(product));
    }

    @GetMapping("/{id}")
    public ProductResponse getProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal User authenticatedUser) {
        return ProductResponse.from(productService.getProduct(id, authenticatedUser.getId()));
    }

    @GetMapping("/shop/{shopId}")
    public List<ProductResponse> getProductsByShop(
            @PathVariable Long shopId,
            @AuthenticationPrincipal User authenticatedUser) {
        return productService.getProductsByShop(shopId, authenticatedUser.getId()).stream()
                .map(ProductResponse::from)
                .toList();
    }

    @PutMapping("/{id}")
    public ProductResponse updateProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody ProductRequest request) {
        Product product = productService.updateProduct(id, authenticatedUser.getId(), request);
        return ProductResponse.from(product);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal User authenticatedUser) {
        productService.deleteProduct(id, authenticatedUser.getId());
        return ResponseEntity.noContent().build();
    }

    public record ProductResponse(
            Long id,
            Long shopId,
            String name,
            String description,
            String category,
            BigDecimal price,
            String unit,
            Integer quantity,
            String imageUrl,
            boolean active,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        private static ProductResponse from(Product product) {
            return new ProductResponse(
                    product.getId(),
                    product.getShopId(),
                    product.getName(),
                    product.getDescription(),
                    product.getCategory(),
                    product.getPrice(),
                    product.getUnit(),
                    product.getQuantity(),
                    product.getImageUrl(),
                    product.isActive(),
                    product.getCreatedAt(),
                    product.getUpdatedAt());
        }
    }
}
