package com.Echo.NearBuy.shop.repository;

import com.Echo.NearBuy.shop.entity.Shop;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShopRepository extends JpaRepository<Shop, Long> {
    List<Shop> findByOwnerId(Long ownerId);

    @Query(value = """
            SELECT s.*
            FROM shops s
            WHERE s.latitude IS NOT NULL
              AND s.longitude IS NOT NULL
              AND 6371 * 2 * ASIN(SQRT(
                    POWER(SIN(RADIANS(s.latitude - :latitude) / 2), 2)
                    + COS(RADIANS(:latitude)) * COS(RADIANS(s.latitude))
                    * POWER(SIN(RADIANS(s.longitude - :longitude) / 2), 2)
              )) <= :radiusKm
            ORDER BY 6371 * 2 * ASIN(SQRT(
                    POWER(SIN(RADIANS(s.latitude - :latitude) / 2), 2)
                    + COS(RADIANS(:latitude)) * COS(RADIANS(s.latitude))
                    * POWER(SIN(RADIANS(s.longitude - :longitude) / 2), 2)
            ))
            """, nativeQuery = true)
    List<Shop> findNearbyShops(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusKm") double radiusKm);
}
