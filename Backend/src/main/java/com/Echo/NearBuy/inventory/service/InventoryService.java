package com.Echo.NearBuy.inventory.service;

import com.Echo.NearBuy.common.enums.Role;
import com.Echo.NearBuy.inventory.entity.Inventory;
import com.Echo.NearBuy.inventory.repository.InventoryRepository;
import com.Echo.NearBuy.product.entity.Product;
import com.Echo.NearBuy.product.entity.ProductUnit;
import com.Echo.NearBuy.product.repository.ProductRepository;
import com.Echo.NearBuy.product.repository.ProductUnitRepository;
import com.Echo.NearBuy.shop.entity.Shop;
import com.Echo.NearBuy.shop.repository.ShopRepository;
import com.Echo.NearBuy.user.entity.User;
import com.Echo.NearBuy.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final ProductUnitRepository productUnitRepository;
    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;

    public InventoryService(
            InventoryRepository inventoryRepository,
            ProductUnitRepository productUnitRepository,
            ProductRepository productRepository,
            ShopRepository shopRepository,
            UserRepository userRepository) {
        this.inventoryRepository = inventoryRepository;
        this.productUnitRepository = productUnitRepository;
        this.productRepository = productRepository;
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Inventory createInventory(Long ownerId, Long shopId, Long productUnitId, Integer availableQuantity) {
        requireShopkeeper(ownerId);
        findOwnedShop(shopId, ownerId);
        ProductUnit productUnit = productUnitRepository.findById(productUnitId)
                .orElseThrow(() -> new EntityNotFoundException("Product unit not found: " + productUnitId));
        Product product = productRepository.findById(productUnit.getProductId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Product not found: " + productUnit.getProductId()));
        if (!product.getShopId().equals(shopId)) {
            throw new EntityNotFoundException("Product unit not found: " + productUnitId);
        }
        if (availableQuantity == null || availableQuantity < 0) {
            throw new IllegalArgumentException("Available quantity must be zero or greater");
        }
        if (inventoryRepository.findByShopIdAndProductUnitId(shopId, productUnitId).isPresent()) {
            throw new IllegalStateException("Inventory already exists for this shop and product unit");
        }

        Inventory inventory = new Inventory();
        inventory.setShopId(shopId);
        inventory.setProductUnitId(productUnitId);
        inventory.setAvailableQuantity(availableQuantity);
        inventory.setReservedQuantity(0);
        return inventoryRepository.save(inventory);
    }

    public List<Inventory> getShopInventory(Long ownerId, Long shopId) {
        requireShopkeeper(ownerId);
        findOwnedShop(shopId, ownerId);
        return inventoryRepository.findByShopId(shopId);
    }

    @Transactional
    public Inventory reserve(Long ownerId, Long inventoryId, Integer quantity) {
        Inventory inventory = findOwnedInventoryForUpdate(ownerId, inventoryId);
        requirePositiveQuantity(quantity);
        if (inventory.getAvailableQuantity() < quantity) {
            throw new IllegalStateException("Insufficient available inventory");
        }
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);
        inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);
        return inventoryRepository.save(inventory);
    }

    @Transactional
    public Inventory commitReservation(Long ownerId, Long inventoryId, Integer quantity) {
        Inventory inventory = findOwnedInventoryForUpdate(ownerId, inventoryId);
        requirePositiveQuantity(quantity);
        if (inventory.getReservedQuantity() < quantity) {
            throw new IllegalStateException("Quantity exceeds reserved inventory");
        }
        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
        return inventoryRepository.save(inventory);
    }

    @Transactional
    public Inventory releaseReservation(Long ownerId, Long inventoryId, Integer quantity) {
        Inventory inventory = findOwnedInventoryForUpdate(ownerId, inventoryId);
        requirePositiveQuantity(quantity);
        if (inventory.getReservedQuantity() < quantity) {
            throw new IllegalStateException("Quantity exceeds reserved inventory");
        }
        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantity);
        return inventoryRepository.save(inventory);
    }

    private Inventory findOwnedInventoryForUpdate(Long ownerId, Long inventoryId) {
        requireShopkeeper(ownerId);
        Inventory inventory = inventoryRepository.findByIdForUpdate(inventoryId)
                .orElseThrow(() -> new EntityNotFoundException("Inventory not found: " + inventoryId));
        findOwnedShop(inventory.getShopId(), ownerId);
        return inventory;
    }

    private Shop findOwnedShop(Long shopId, Long ownerId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new EntityNotFoundException("Shop not found: " + shopId));
        if (!shop.getOwnerId().equals(ownerId)) {
            throw new EntityNotFoundException("Shop not found: " + shopId);
        }
        return shop;
    }

    private void requireShopkeeper(Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new EntityNotFoundException("Shop owner not found: " + ownerId));
        if (!owner.isEnabled() || owner.getRole() != Role.SHOPKEEPER) {
            throw new AccessDeniedException("Only an enabled shopkeeper can manage inventory");
        }
    }

    private void requirePositiveQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
    }
}
