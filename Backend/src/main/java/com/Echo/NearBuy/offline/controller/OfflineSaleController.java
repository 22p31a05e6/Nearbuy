package com.Echo.NearBuy.offline.controller;

import com.Echo.NearBuy.offline.dto.CreateOfflineSaleRequest;
import com.Echo.NearBuy.offline.entity.OfflineSale;
import com.Echo.NearBuy.offline.entity.OfflineSaleItem;
import com.Echo.NearBuy.offline.enums.OfflinePaymentMethod;
import com.Echo.NearBuy.offline.service.OfflineSaleService;
import com.Echo.NearBuy.user.entity.User;
import jakarta.validation.Valid;
import java.math.BigDecimal;
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
@RequestMapping("/api/offline-sales")
public class OfflineSaleController {
    private final OfflineSaleService offlineSaleService;

    public OfflineSaleController(OfflineSaleService offlineSaleService) {
        this.offlineSaleService = offlineSaleService;
    }

    @PostMapping
    public ResponseEntity<OfflineSaleResponse> createSale(
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody CreateOfflineSaleRequest request) {
        OfflineSale sale = offlineSaleService.createSale(authenticatedUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OfflineSaleResponse.from(
                        sale, offlineSaleService.getSaleItems(authenticatedUser.getId(), sale.getId())));
    }

    @GetMapping("/shops/{shopId}")
    public List<OfflineSaleResponse> getShopSales(
            @PathVariable Long shopId,
            @AuthenticationPrincipal User authenticatedUser) {
        return offlineSaleService.getShopSales(authenticatedUser.getId(), shopId).stream()
                .map(sale -> OfflineSaleResponse.from(
                        sale,
                        offlineSaleService.getSaleItems(authenticatedUser.getId(), sale.getId())))
                .toList();
    }

    public record OfflineSaleResponse(
            Long id,
            Long shopId,
            BigDecimal totalAmount,
            OfflinePaymentMethod paymentMethod,
            LocalDateTime createdAt,
            List<OfflineSaleItemResponse> items) {
        private static OfflineSaleResponse from(OfflineSale sale, List<OfflineSaleItem> items) {
            return new OfflineSaleResponse(
                    sale.getId(),
                    sale.getShopId(),
                    sale.getTotalAmount(),
                    sale.getPaymentMethod(),
                    sale.getCreatedAt(),
                    items.stream().map(OfflineSaleItemResponse::from).toList());
        }
    }

    public record OfflineSaleItemResponse(
            Long id,
            Long productUnitId,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice) {
        private static OfflineSaleItemResponse from(OfflineSaleItem item) {
            return new OfflineSaleItemResponse(
                    item.getId(),
                    item.getProductUnitId(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getTotalPrice());
        }
    }
}
