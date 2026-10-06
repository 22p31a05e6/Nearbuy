package com.Echo.NearBuy.order.service;

import com.Echo.NearBuy.cart.entity.Cart;
import com.Echo.NearBuy.cart.entity.CartItem;
import com.Echo.NearBuy.cart.repository.CartItemRepository;
import com.Echo.NearBuy.cart.repository.CartRepository;
import com.Echo.NearBuy.common.enums.OrderStatus;
import com.Echo.NearBuy.common.enums.PaymentMethod;
import com.Echo.NearBuy.common.enums.PaymentStatus;
import com.Echo.NearBuy.common.enums.Role;
import com.Echo.NearBuy.inventory.entity.Inventory;
import com.Echo.NearBuy.inventory.repository.InventoryRepository;
import com.Echo.NearBuy.order.dto.CreateOrderRequest;
import com.Echo.NearBuy.order.dto.CheckoutCartRequest;
import com.Echo.NearBuy.order.entity.Order;
import com.Echo.NearBuy.order.entity.OrderItem;
import com.Echo.NearBuy.order.repository.OrderItemRepository;
import com.Echo.NearBuy.order.repository.OrderRepository;
import com.Echo.NearBuy.payment.service.PaymentService;
import com.Echo.NearBuy.product.entity.Product;
import com.Echo.NearBuy.product.entity.ProductUnit;
import com.Echo.NearBuy.product.repository.ProductRepository;
import com.Echo.NearBuy.product.repository.ProductUnitRepository;
import com.Echo.NearBuy.shop.repository.ShopRepository;
import com.Echo.NearBuy.user.entity.User;
import com.Echo.NearBuy.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderService {
    private static final BigDecimal ZERO_AMOUNT = new BigDecimal("0.00");

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final ProductUnitRepository productUnitRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentService paymentService;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    public OrderService(
            UserRepository userRepository,
            ShopRepository shopRepository,
            ProductRepository productRepository,
            ProductUnitRepository productUnitRepository,
            InventoryRepository inventoryRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentService paymentService,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository) {
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
        this.productRepository = productRepository;
        this.productUnitRepository = productUnitRepository;
        this.inventoryRepository = inventoryRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentService = paymentService;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
    }

    @Transactional
    public Order checkoutCart(Long customerId, CheckoutCartRequest request) {
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Cart not found for customer: " + customerId));
        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Cannot checkout an empty cart");
        }

        CartItem firstItem = cartItems.get(0);
        ProductUnit firstUnit = productUnitRepository.findById(firstItem.getProductUnitId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Product unit not found: " + firstItem.getProductUnitId()));
        Product firstProduct = productRepository.findById(firstUnit.getProductId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Product not found: " + firstUnit.getProductId()));
        List<CreateOrderRequest.OrderItemRequest> items = cartItems.stream()
                .map(item -> new CreateOrderRequest.OrderItemRequest(
                        item.getProductUnitId(), item.getQuantity()))
                .toList();

        CreateOrderRequest orderRequest = new CreateOrderRequest(
                firstProduct.getShopId(),
                items,
                request.deliveryAddress(),
                request.latitude(),
                request.longitude(),
                request.deliveryFee(),
                request.paymentMethod());
        Order order = createOrder(customerId, orderRequest);
        cartItemRepository.deleteAll(cartItems);
        return order;
    }

    @Transactional
    public Order createOrder(Long customerId, CreateOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Order request must not be null");
        }
        if (request.paymentMethod() != PaymentMethod.CASH_ON_DELIVERY
                && request.paymentMethod() != PaymentMethod.ONLINE) {
            throw new IllegalArgumentException("Supported payment methods are CASH_ON_DELIVERY and ONLINE");
        }
        requireCustomer(customerId);
        if (!shopRepository.existsById(request.shopId())) {
            throw new EntityNotFoundException("Shop not found: " + request.shopId());
        }

        BigDecimal deliveryFee = request.deliveryFee().setScale(2, RoundingMode.HALF_UP);
        Map<Long, Integer> quantitiesByUnit = new LinkedHashMap<>();
        for (CreateOrderRequest.OrderItemRequest item : request.items()) {
            quantitiesByUnit.merge(item.productUnitId(), item.quantity(), Math::addExact);
        }

        List<Long> unitIds = quantitiesByUnit.keySet().stream().sorted().toList();
        List<OrderLineSnapshot> snapshots = new ArrayList<>(unitIds.size());
        BigDecimal subtotal = ZERO_AMOUNT;

        for (Long productUnitId : unitIds) {
            ProductUnit productUnit = productUnitRepository.findById(productUnitId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Product unit not found: " + productUnitId));
            Product product = productRepository.findById(productUnit.getProductId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Product not found: " + productUnit.getProductId()));
            if (!product.getShopId().equals(request.shopId()) || !product.isActive()) {
                throw new EntityNotFoundException("Product unit not found: " + productUnitId);
            }

            Inventory inventory = inventoryRepository
                    .findByShopIdAndProductUnitIdForUpdate(request.shopId(), productUnitId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Inventory not found for product unit: " + productUnitId));
            int quantity = quantitiesByUnit.get(productUnitId);
            if (inventory.getAvailableQuantity() < quantity) {
                throw new IllegalStateException(
                        "Insufficient stock for product unit: " + productUnitId);
            }

            BigDecimal unitPrice = productUnit.getPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity))
                    .setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(totalPrice);
            snapshots.add(new OrderLineSnapshot(
                    product.getId(), productUnit.getId(), quantity, unitPrice, totalPrice, inventory));
        }

        BigDecimal totalAmount = subtotal.add(deliveryFee).setScale(2, RoundingMode.HALF_UP);
        for (OrderLineSnapshot snapshot : snapshots) {
            Inventory inventory = snapshot.inventory();
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() - snapshot.quantity());
            inventory.setReservedQuantity(inventory.getReservedQuantity() + snapshot.quantity());
            inventoryRepository.save(inventory);
        }

        Order order = new Order();
        order.setCustomerId(customerId);
        order.setShopId(request.shopId());
        order.setTotalAmount(totalAmount);
        order.setDeliveryFee(deliveryFee);
        order.setStatus(OrderStatus.PLACED.name());
        order.setPaymentStatus(PaymentStatus.PENDING.name());
        order.setPaymentMethod(request.paymentMethod().name());
        order.setDeliveryAddress(request.deliveryAddress().strip());
        order.setLatitude(request.latitude());
        order.setLongitude(request.longitude());
        Order savedOrder = orderRepository.save(order);

        List<OrderItem> orderItems = snapshots.stream()
                .map(snapshot -> toOrderItem(savedOrder.getId(), snapshot))
                .toList();
        orderItemRepository.saveAll(orderItems);

        paymentService.createPendingPayment(savedOrder, request.paymentMethod());

        return savedOrder;
    }

    private void requireCustomer(Long customerId) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found: " + customerId));
        if (!customer.isEnabled() || customer.getRole() != Role.CUSTOMER) {
            throw new AccessDeniedException("Only an enabled customer can place orders");
        }
    }

    private OrderItem toOrderItem(Long orderId, OrderLineSnapshot snapshot) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setProductId(snapshot.productId());
        item.setProductUnitId(snapshot.productUnitId());
        item.setQuantity(snapshot.quantity());
        item.setUnitPrice(snapshot.unitPrice());
        item.setTotalPrice(snapshot.totalPrice());
        return item;
    }

    private record OrderLineSnapshot(
            Long productId,
            Long productUnitId,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            Inventory inventory) {}
}
