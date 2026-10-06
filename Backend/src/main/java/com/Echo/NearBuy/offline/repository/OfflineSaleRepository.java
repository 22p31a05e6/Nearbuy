package com.Echo.NearBuy.offline.repository;

import com.Echo.NearBuy.offline.entity.OfflineSale;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflineSaleRepository extends JpaRepository<OfflineSale, Long> {
    List<OfflineSale> findByShopIdOrderByCreatedAtDesc(Long shopId);
}
