package com.Echo.NearBuy.product.repository;

import com.Echo.NearBuy.product.entity.Product;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByShopId(Long shopId);

    List<Product> findByShopIdAndActiveTrue(Long shopId);
}
