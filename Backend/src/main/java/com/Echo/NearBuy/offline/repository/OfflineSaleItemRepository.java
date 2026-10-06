package com.Echo.NearBuy.offline.repository;

import com.Echo.NearBuy.offline.entity.OfflineSaleItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflineSaleItemRepository extends JpaRepository<OfflineSaleItem, Long> {
    List<OfflineSaleItem> findByOfflineSaleId(Long offlineSaleId);
}
