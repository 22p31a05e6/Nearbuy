package com.Echo.NearBuy.offline.service;

import com.Echo.NearBuy.common.enums.Role;
import com.Echo.NearBuy.inventory.entity.Inventory;
import com.Echo.NearBuy.inventory.repository.InventoryRepository;
import com.Echo.NearBuy.offline.dto.CreateOfflineSaleRequest;
import com.Echo.NearBuy.offline.entity.OfflineSale;
import com.Echo.NearBuy.offline.entity.OfflineSaleItem;
import com.Echo.NearBuy.offline.repository.OfflineSaleItemRepository;
import com.Echo.NearBuy.offline.repository.OfflineSaleRepository;
import com.Echo.NearBuy.product.entity.Product;
import com.Echo.NearBuy.product.entity.ProductUnit;
import com.Echo.NearBuy.product.repository.ProductRepository;
import com.Echo.NearBuy.product.repository.ProductUnitRepository;
import com.Echo.NearBuy.shop.entity.Shop;
import com.Echo.NearBuy.shop.repository.ShopRepository;
import com.Echo.NearBuy.user.entity.User;
import com.Echo.NearBuy.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfflineSaleService {
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2);

    private final OfflineSaleRepository offlineSaleRepository;
    private final OfflineSaleItemRepository offlineSaleItemRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductUnitRepository productUnitRepository;
    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;

    public OfflineSaleService(
            OfflineSaleRepository offlineSaleRepository,
            OfflineSaleItemRepository offlineSaleItemRepository,
            InventoryRepository inventoryRepository,
            ProductUnitRepository productUnitRepository,
            ProductRepository productRepository,
            ShopRepository shopRepository,
            UserRepository userRepository) {
        this.offlineSaleRepository = offlineSaleRepository;
        this.offlineSaleItemRepository = offlineSaleItemRepository;
        this.inventoryRepository = inventoryRepository;
        this.productUnitRepository = productUnitRepository;
        this.productRepository = productRepository;
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public OfflineSale createSale(Long shopkeeperId, CreateOfflineSaleRequest request) {
        requireShopkeeper(shopkeeperId);
        findOwnedShop(request.shopId(), shopkeeperId);

        Map<Long, Integer> quantitiesByUnit = new LinkedHashMap<>();
        for (CreateOfflineSaleRequest.OfflineSaleItemRequest item : request.items()) {
            quantitiesByUnit.merge(item.productUnitId(), item.quantity(), Math::addExact);
        }

        List<SaleLine> saleLines = new ArrayList<>(quantitiesByUnit.size());
        BigDecimal totalAmount = ZERO_AMOUNT;
        for (Long productUnitId : quantitiesByUnit.keySet().stream().sorted().toList()) {
            ProductUnit productUnit = productUnitRepository.findById(productUnitId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Product unit not found: " + productUnitId));
            Product product = productRepository.findById(productUnit.getProductId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Product not found: " + productUnit.getProductId()));
            if (!product.getShopId().equals(request.shopId()) || !product.isActive()) {
                throw new EntityNotFoundException("Product unit not found: " + productUnitId);
            }

            Inventory inventory = inventoryRepository
                    .findByShopIdAndProductUnitIdForUpdate(request.shopId(), productUnitId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Inventory not found for product unit: " + productUnitId));
            int quantity = quantitiesByUnit.get(productUnitId);
            if (inventory.getAvailableQuantity() < quantity) {
                throw new IllegalStateException(
                        "Insufficient stock for product unit: " + productUnitId);
            }

            BigDecimal unitPrice = productUnit.getPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity))
                    .setScale(2, RoundingMode.HALF_UP);
            totalAmount = totalAmount.add(lineTotal);
            saleLines.add(new SaleLine(productUnitId, quantity, unitPrice, lineTotal, inventory));
        }

        OfflineSale sale = new OfflineSale();
        sale.setShopId(request.shopId());
        sale.setPaymentMethod(request.paymentMethod());
        sale.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        OfflineSale savedSale = offlineSaleRepository.save(sale);

        List<OfflineSaleItem> saleItems = saleLines.stream()
                .map(line -> toSaleItem(savedSale.getId(), line))
                .toList();
        offlineSaleItemRepository.saveAll(saleItems);

        for (SaleLine line : saleLines) {
            Inventory inventory = line.inventory();
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() - line.quantity());
            inventoryRepository.save(inventory);
        }

        return savedSale;
    }

    @Transactional(readOnly = true)
    public List<OfflineSale> getShopSales(Long shopkeeperId, Long shopId) {
        requireShopkeeper(shopkeeperId);
        findOwnedShop(shopId, shopkeeperId);
        return offlineSaleRepository.findByShopIdOrderByCreatedAtDesc(shopId);
    }

    @Transactional(readOnly = true)
    public List<OfflineSaleItem> getSaleItems(Long shopkeeperId, Long saleId) {
        requireShopkeeper(shopkeeperId);
        OfflineSale sale = offlineSaleRepository.findById(saleId)
                .orElseThrow(() -> new EntityNotFoundException("Offline sale not found: " + saleId));
        findOwnedShop(sale.getShopId(), shopkeeperId);
        return offlineSaleItemRepository.findByOfflineSaleId(saleId);
    }

    private OfflineSaleItem toSaleItem(Long saleId, SaleLine line) {
        OfflineSaleItem item = new OfflineSaleItem();
        item.setOfflineSaleId(saleId);
        item.setProductUnitId(line.productUnitId());
        item.setQuantity(line.quantity());
        item.setUnitPrice(line.unitPrice());
        item.setTotalPrice(line.totalPrice());
        return item;
    }

    private Shop findOwnedShop(Long shopId, Long shopkeeperId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new EntityNotFoundException("Shop not found: " + shopId));
        if (!shop.getOwnerId().equals(shopkeeperId)) {
            throw new EntityNotFoundException("Shop not found: " + shopId);
        }
        return shop;
    }

    private void requireShopkeeper(Long shopkeeperId) {
        User shopkeeper = userRepository.findById(shopkeeperId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Shopkeeper not found: " + shopkeeperId));
        if (!shopkeeper.isEnabled() || shopkeeper.getRole() != Role.SHOPKEEPER) {
            throw new AccessDeniedException("Only an enabled shopkeeper can manage offline sales");
        }
    }

    private record SaleLine(
            Long productUnitId,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            Inventory inventory) {}
}
