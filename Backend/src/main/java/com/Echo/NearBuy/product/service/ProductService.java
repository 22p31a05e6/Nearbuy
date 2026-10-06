package com.Echo.NearBuy.product.service;

import com.Echo.NearBuy.common.enums.Role;
import com.Echo.NearBuy.product.dto.ProductRequest;
import com.Echo.NearBuy.product.entity.Product;
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
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductUnitRepository productUnitRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;

    public ProductService(
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
    public Product createProduct(Long ownerId, ProductRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireShopkeeper(ownerId);

        Shop shop = findOwnedShop(request.shopId(), ownerId);

        Product product = new Product();
        product.setShopId(shop.getId());
        applyRequest(product, request);
        return productRepository.save(product);
    }

    public Product getProduct(Long productId, Long ownerId) {
        requireShopkeeper(ownerId);
        return findOwnedProduct(productId, ownerId);
    }

    public List<Product> getProductsByShop(Long shopId, Long ownerId) {
        requireShopkeeper(ownerId);
        findOwnedShop(shopId, ownerId);
        return productRepository.findByShopId(shopId);
    }

    @Transactional
    public Product updateProduct(Long productId, Long ownerId, ProductRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireShopkeeper(ownerId);

        Product product = findOwnedProduct(productId, ownerId);
        if (!product.getShopId().equals(request.shopId())) {
            Shop shop = findOwnedShop(request.shopId(), ownerId);
            product.setShopId(shop.getId());
        }
        applyRequest(product, request);
        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(Long productId, Long ownerId) {
        requireShopkeeper(ownerId);
        Product product = findOwnedProduct(productId, ownerId);
        productUnitRepository.deleteAll(productUnitRepository.findByProductId(productId));
        productRepository.delete(product);
    }

    private void requireShopkeeper(Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new EntityNotFoundException("Shop owner not found: " + ownerId));
        if (!owner.isEnabled() || owner.getRole() != Role.SHOPKEEPER) {
            throw new AccessDeniedException("Only an enabled shopkeeper can manage products");
        }
    }

    private Shop findOwnedShop(Long shopId, Long ownerId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new EntityNotFoundException("Shop not found: " + shopId));
        if (!shop.getOwnerId().equals(ownerId)) {
            throw new EntityNotFoundException("Shop not found: " + shopId);
        }
        return shop;
    }

    private Product findOwnedProduct(Long productId, Long ownerId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));
        findOwnedShop(product.getShopId(), ownerId);
        return product;
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setCategory(request.category().trim());
        product.setPrice(request.price());
        product.setUnit(request.unit().trim());
        product.setQuantity(request.quantity());
        product.setImageUrl(request.imageUrl());
        product.setActive(request.isActive());
    }
}
