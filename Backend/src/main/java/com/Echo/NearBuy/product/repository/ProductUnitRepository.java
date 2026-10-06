package com.Echo.NearBuy.product.repository;

import com.Echo.NearBuy.product.entity.ProductUnit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductUnitRepository extends JpaRepository<ProductUnit, Long> {
    List<ProductUnit> findByProductId(Long productId);

    List<ProductUnit> findByProductIdAndUnitType(Long productId, String unitType);
}
