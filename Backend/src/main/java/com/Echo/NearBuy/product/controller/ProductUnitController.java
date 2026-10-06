package com.Echo.NearBuy.product.controller;

import com.Echo.NearBuy.product.dto.ProductUnitRequest;
import com.Echo.NearBuy.product.entity.ProductUnit;
import com.Echo.NearBuy.product.service.ProductUnitService;
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
public class ProductUnitController {
    private final ProductUnitService productUnitService;

    public ProductUnitController(ProductUnitService productUnitService) {
        this.productUnitService = productUnitService;
    }

    @PostMapping("/{productId}/units")
    public ResponseEntity<ProductUnitResponse> addUnit(
            @PathVariable Long productId,
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody ProductUnitRequest request) {
        ProductUnit unit = productUnitService.createProductUnit(authenticatedUser.getId(), productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductUnitResponse.from(unit));
    }

    @GetMapping("/{productId}/units")
    public List<ProductUnitResponse> getUnits(
            @PathVariable Long productId,
            @AuthenticationPrincipal User authenticatedUser) {
        return productUnitService.getProductUnits(authenticatedUser.getId(), productId).stream()
                .map(ProductUnitResponse::from)
                .toList();
    }

    @PutMapping("/{productId}/units/{unitId}")
    public ProductUnitResponse updateUnit(
            @PathVariable Long productId,
            @PathVariable Long unitId,
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody ProductUnitRequest request) {
        ProductUnit unit = productUnitService.updateProductUnit(authenticatedUser.getId(), productId, unitId, request);
        return ProductUnitResponse.from(unit);
    }

    @DeleteMapping("/{productId}/units/{unitId}")
    public ResponseEntity<Void> deleteUnit(
            @PathVariable Long productId,
            @PathVariable Long unitId,
            @AuthenticationPrincipal User authenticatedUser) {
        productUnitService.deleteProductUnit(authenticatedUser.getId(), productId, unitId);
        return ResponseEntity.noContent().build();
    }

    public record ProductUnitResponse(
            Long id,
            Long productId,
            String unitType,
            BigDecimal unitValue,
            BigDecimal price,
            Integer stockQuantity,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        private static ProductUnitResponse from(ProductUnit unit) {
            return new ProductUnitResponse(
                    unit.getId(),
                    unit.getProductId(),
                    unit.getUnitType(),
                    unit.getUnitValue(),
                    unit.getPrice(),
                    unit.getStockQuantity(),
                    unit.getCreatedAt(),
                    unit.getUpdatedAt());
        }
    }
}
