package com.Echo.NearBuy.order.service;

import com.Echo.NearBuy.common.enums.OrderStatus;
import com.Echo.NearBuy.common.enums.PaymentMethod;
import com.Echo.NearBuy.common.enums.PaymentStatus;
import com.Echo.NearBuy.common.enums.Role;
import com.Echo.NearBuy.order.entity.Order;
import com.Echo.NearBuy.order.repository.OrderRepository;
import com.Echo.NearBuy.shop.repository.ShopRepository;
import com.Echo.NearBuy.user.entity.User;
import com.Echo.NearBuy.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PackingService {
    private final OrderRepository orderRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;

    public PackingService(
            OrderRepository orderRepository,
            ShopRepository shopRepository,
            UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Order startPacking(Long shopkeeperId, Long orderId) {
        Order order = findPaidOnlineOrder(shopkeeperId, orderId);
        requireStatus(order, OrderStatus.ACCEPTED);
        order.setStatus(OrderStatus.PACKING.name());
        return orderRepository.save(order);
    }

    @Transactional
    public Order markPacked(Long shopkeeperId, Long orderId) {
        Order order = findPaidOnlineOrder(shopkeeperId, orderId);
        requireStatus(order, OrderStatus.PACKING);
        order.setStatus(OrderStatus.DELIVERY_REQUESTED.name());
        return orderRepository.save(order);
    }

    private Order findPaidOnlineOrder(Long shopkeeperId, Long orderId) {
        User shopkeeper = userRepository.findById(shopkeeperId)
                .orElseThrow(() -> new EntityNotFoundException("Shopkeeper not found: " + shopkeeperId));
        if (!shopkeeper.isEnabled() || shopkeeper.getRole() != Role.SHOPKEEPER) {
            throw new AccessDeniedException("Only an enabled shopkeeper can update packing status");
        }
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
        boolean ownsOrderShop = shopRepository.findById(order.getShopId())
                .map(shop -> shop.getOwnerId().equals(shopkeeperId))
                .orElse(false);
        if (!ownsOrderShop) {
            throw new EntityNotFoundException("Order not found: " + orderId);
        }
        if (!PaymentMethod.ONLINE.name().equals(order.getPaymentMethod())
                || !PaymentStatus.SUCCESS.name().equals(order.getPaymentStatus())) {
            throw new IllegalStateException("Only successfully paid online orders can enter packing");
        }
        return order;
    }

    private void requireStatus(Order order, OrderStatus expectedStatus) {
        if (!expectedStatus.name().equals(order.getStatus())) {
            throw new IllegalStateException(
                    "Order must be " + expectedStatus + " before it can advance");
        }
    }
}
