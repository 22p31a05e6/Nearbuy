package com.Echo.NearBuy.shop.service;

import com.Echo.NearBuy.common.enums.Role;
import com.Echo.NearBuy.shop.dto.CreateShopRequest;
import com.Echo.NearBuy.shop.entity.Shop;
import com.Echo.NearBuy.shop.repository.ShopRepository;
import com.Echo.NearBuy.user.entity.User;
import com.Echo.NearBuy.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ShopService {
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;

    public ShopService(ShopRepository shopRepository, UserRepository userRepository) {
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Shop createShop(Long ownerId, CreateShopRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireShopkeeper(ownerId);

        Shop shop = new Shop();
        shop.setOwnerId(ownerId);
        applyRequest(shop, request);
        return shopRepository.save(shop);
    }

    public Shop getShop(Long shopId, Long ownerId) {
        requireShopkeeper(ownerId);
        return findOwnedShop(shopId, ownerId);
    }

    @Transactional
    public Shop updateShop(Long shopId, Long ownerId, CreateShopRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireShopkeeper(ownerId);

        Shop shop = findOwnedShop(shopId, ownerId);
        applyRequest(shop, request);
        return shopRepository.save(shop);
    }

    @Transactional
    public void deleteShop(Long shopId, Long ownerId) {
        requireShopkeeper(ownerId);
        Shop shop = findOwnedShop(shopId, ownerId);
        shopRepository.delete(shop);
    }

    private void requireShopkeeper(Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new EntityNotFoundException("Shop owner not found: " + ownerId));
        if (!owner.isEnabled() || owner.getRole() != Role.SHOPKEEPER) {
            throw new AccessDeniedException("Only an enabled shopkeeper can manage shops");
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

    private void applyRequest(Shop shop, CreateShopRequest request) {
        shop.setShopName(request.shopName().strip());
        shop.setDescription(request.description());
        shop.setPhone(request.phone());
        shop.setAddress(request.address());
        shop.setLatitude(request.latitude());
        shop.setLongitude(request.longitude());
    }
}
