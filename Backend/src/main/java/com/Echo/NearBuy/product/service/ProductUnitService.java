package com.Echo.NearBuy.product.service;

import com.Echo.NearBuy.common.enums.Role;
import com.Echo.NearBuy.product.dto.ProductUnitRequest;
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
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductUnitService {
    private final ProductRepository productRepository;
    private final ProductUnitRepository productUnitRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;

    public ProductUnitService(
            ProductRepository productRepository,
            ProductUnitRepository productUnitRepository,
            ShopRepository shopRepository,
            UserRepository userRepository) {
        this.productRepository = productRepository;
        this.productUnitRepository = productUnitRepository;
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ProductUnit createProductUnit(Long ownerId, Long productId, ProductUnitRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireShopkeeper(ownerId);

        Product product = findOwnedProduct(productId, ownerId);
        ProductUnit unit = new ProductUnit();
        unit.setProductId(product.getId());
        applyRequest(unit, request);
        return productUnitRepository.save(unit);
    }

    public List<ProductUnit> getProductUnits(Long ownerId, Long productId) {
        requireShopkeeper(ownerId);
        findOwnedProduct(productId, ownerId);
        return productUnitRepository.findByProductId(productId);
    }

    @Transactional
    public ProductUnit updateProductUnit(Long ownerId, Long productId, Long unitId, ProductUnitRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireShopkeeper(ownerId);

        findOwnedProduct(productId, ownerId);
        ProductUnit unit = productUnitRepository.findById(unitId)
                .orElseThrow(() -> new EntityNotFoundException("Product unit not found: " + unitId));

        if (!unit.getProductId().equals(productId)) {
            throw new EntityNotFoundException("Product unit not found: " + unitId);
        }

        applyRequest(unit, request);
        return productUnitRepository.save(unit);
    }

    @Transactional
    public void deleteProductUnit(Long ownerId, Long productId, Long unitId) {
        requireShopkeeper(ownerId);
        findOwnedProduct(productId, ownerId);
        ProductUnit unit = productUnitRepository.findById(unitId)
                .orElseThrow(() -> new EntityNotFoundException("Product unit not found: " + unitId));
        if (!unit.getProductId().equals(productId)) {
            throw new EntityNotFoundException("Product unit not found: " + unitId);
        }
        productUnitRepository.delete(unit);
    }

    private void requireShopkeeper(Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new EntityNotFoundException("Shop owner not found: " + ownerId));
        if (!owner.isEnabled() || owner.getRole() != Role.SHOPKEEPER) {
            throw new AccessDeniedException("Only an enabled shopkeeper can manage product units");
        }
    }

    private Product findOwnedProduct(Long productId, Long ownerId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));
        Shop shop = shopRepository.findById(product.getShopId())
                .orElseThrow(() -> new EntityNotFoundException("Shop not found: " + product.getShopId()));
        if (!shop.getOwnerId().equals(ownerId)) {
            throw new EntityNotFoundException("Product not found: " + productId);
        }
        return product;
    }

    private void applyRequest(ProductUnit unit, ProductUnitRequest request) {
        unit.setUnitType(request.unitType().trim());
        unit.setUnitValue(request.unitValue());
        unit.setPrice(request.price());
        unit.setStockQuantity(request.stockQuantity());
    }
}
