package com.Echo.NearBuy.shop.service;

import com.Echo.NearBuy.common.enums.Role;
import com.Echo.NearBuy.shop.entity.Shop;
import com.Echo.NearBuy.shop.repository.ShopRepository;
import com.Echo.NearBuy.user.entity.User;
import com.Echo.NearBuy.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ShopSearchService {
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;

    public ShopSearchService(ShopRepository shopRepository, UserRepository userRepository) {
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
    }

    public List<NearbyShop> findNearbyShops(
            Long customerId, BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm) {
        requireCustomer(customerId);
        validateCoordinates(latitude, longitude);
        if (radiusKm == null || radiusKm.signum() <= 0) {
            throw new IllegalArgumentException("Search radius must be greater than zero");
        }

        double customerLatitude = latitude.doubleValue();
        double customerLongitude = longitude.doubleValue();
        double radius = radiusKm.doubleValue();
        if (!Double.isFinite(radius)) {
            throw new IllegalArgumentException("Search radius is too large");
        }
        return shopRepository.findNearbyShops(customerLatitude, customerLongitude, radius).stream()
                .map(shop -> toNearbyShop(shop, customerLatitude, customerLongitude))
                .filter(shop -> shop.distanceKm().compareTo(radiusKm) <= 0)
                .toList();
    }

    private NearbyShop toNearbyShop(Shop shop, double latitude, double longitude) {
        BigDecimal distanceKm = BigDecimal.valueOf(distanceKm(
                        latitude,
                        longitude,
                        shop.getLatitude().doubleValue(),
                        shop.getLongitude().doubleValue()))
                .setScale(2, RoundingMode.HALF_UP);
        return new NearbyShop(
                shop.getId(),
                shop.getOwnerId(),
                shop.getShopName(),
                shop.getDescription(),
                shop.getPhone(),
                shop.getAddress(),
                shop.getLatitude(),
                shop.getLongitude(),
                shop.getStatus(),
                distanceKm);
    }

    private double distanceKm(
            double firstLatitude,
            double firstLongitude,
            double secondLatitude,
            double secondLongitude) {
        double latitudeDelta = Math.toRadians(secondLatitude - firstLatitude);
        double longitudeDelta = Math.toRadians(secondLongitude - firstLongitude);
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(Math.toRadians(firstLatitude))
                        * Math.cos(Math.toRadians(secondLatitude))
                        * Math.pow(Math.sin(longitudeDelta / 2), 2);
        haversine = Math.min(1, Math.max(0, haversine));
        return EARTH_RADIUS_KM * 2 * Math.atan2(
                Math.sqrt(haversine), Math.sqrt(1 - haversine));
    }

    private void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0
                || longitude == null || longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("Valid latitude and longitude are required");
        }
    }

    private void requireCustomer(Long customerId) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found: " + customerId));
        if (!customer.isEnabled() || customer.getRole() != Role.CUSTOMER) {
            throw new AccessDeniedException("Only an enabled customer can search nearby shops");
        }
    }

    public record NearbyShop(
            Long id,
            Long ownerId,
            String shopName,
            String description,
            String phone,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            String status,
            BigDecimal distanceKm) {}
}
