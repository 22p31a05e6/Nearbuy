package com.Echo.NearBuy.cart.repository;

import com.Echo.NearBuy.cart.entity.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCartId(Long cartId);

    Optional<CartItem> findByCartIdAndProductUnitId(Long cartId, Long productUnitId);
}
