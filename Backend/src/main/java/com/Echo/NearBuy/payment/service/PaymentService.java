package com.Echo.NearBuy.payment.service;

import com.Echo.NearBuy.common.enums.PaymentMethod;
import com.Echo.NearBuy.common.enums.PaymentStatus;
import com.Echo.NearBuy.common.enums.Role;
import com.Echo.NearBuy.payment.dto.RazorpayCheckoutResponse;
import com.Echo.NearBuy.payment.dto.VerifyRazorpayPaymentRequest;
import com.Echo.NearBuy.order.entity.Order;
import com.Echo.NearBuy.order.repository.OrderRepository;
import com.Echo.NearBuy.payment.entity.Payment;
import com.Echo.NearBuy.payment.repository.PaymentRepository;
import com.Echo.NearBuy.shop.entity.Shop;
import com.Echo.NearBuy.shop.repository.ShopRepository;
import com.Echo.NearBuy.user.entity.User;
import com.Echo.NearBuy.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final RazorpayGatewayService razorpayGatewayService;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            ShopRepository shopRepository,
            UserRepository userRepository,
            RazorpayGatewayService razorpayGatewayService) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.razorpayGatewayService = razorpayGatewayService;
    }

    @Transactional
    public Payment createPendingPayment(Order order, PaymentMethod paymentMethod) {
        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setAmount(order.getTotalAmount());
        payment.setPaymentMethod(paymentMethod);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        return paymentRepository.save(payment);
    }

    public Payment getCustomerPayment(Long customerId, Long orderId) {
        requireRole(customerId, Role.CUSTOMER, "Only customers can access customer payments");
        Order order = findOrder(orderId);
        if (!order.getCustomerId().equals(customerId)) {
            throw new EntityNotFoundException("Payment not found for order: " + orderId);
        }
        return findPayment(orderId);
    }

    @Transactional
    public RazorpayCheckoutResponse createRazorpayOrder(Long customerId, Long orderId) {
        requireRole(customerId, Role.CUSTOMER, "Only customers can initiate online payments");
        Order order = findOrder(orderId);
        if (!order.getCustomerId().equals(customerId)) {
            throw new EntityNotFoundException("Order not found: " + orderId);
        }
        Payment payment = findPayment(orderId);
        if (payment.getPaymentMethod() != PaymentMethod.ONLINE) {
            throw new IllegalStateException("Razorpay is only available for ONLINE payments");
        }
        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment is not pending");
        }

        String gatewayOrderId = payment.getGatewayOrderId();
        if (gatewayOrderId == null || gatewayOrderId.isBlank()) {
            RazorpayGatewayService.GatewayOrder gatewayOrder =
                    razorpayGatewayService.createOrder(orderId, payment.getAmount());
            gatewayOrderId = gatewayOrder.id();
            payment.setGatewayOrderId(gatewayOrderId);
            paymentRepository.save(payment);
        }

        return new RazorpayCheckoutResponse(
                orderId,
                gatewayOrderId,
                razorpayGatewayService.getKeyId(),
                payment.getAmount().movePointRight(2).longValueExact(),
                "INR");
    }

    @Transactional
    public Payment verifyRazorpayPayment(
            Long customerId,
            Long orderId,
            VerifyRazorpayPaymentRequest request) {
        requireRole(customerId, Role.CUSTOMER, "Only customers can verify their payments");
        Order order = findOrder(orderId);
        if (!order.getCustomerId().equals(customerId)) {
            throw new EntityNotFoundException("Order not found: " + orderId);
        }
        Payment payment = findPayment(orderId);
        if (payment.getPaymentMethod() != PaymentMethod.ONLINE) {
            throw new IllegalStateException("Razorpay verification is only valid for ONLINE payments");
        }
        if (payment.getGatewayOrderId() == null || payment.getGatewayOrderId().isBlank()) {
            throw new IllegalStateException("Razorpay order has not been created for this payment");
        }
        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment is not pending");
        }

        razorpayGatewayService.verifyPayment(
                payment.getGatewayOrderId(),
                request.razorpayPaymentId(),
                request.razorpaySignature(),
                payment.getAmount());

        payment.setTransactionId(request.razorpayPaymentId());
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        order.setPaymentStatus(PaymentStatus.SUCCESS.name());
        orderRepository.save(order);
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment collectCashOnDelivery(Long shopkeeperId, Long orderId) {
        requireRole(shopkeeperId, Role.SHOPKEEPER, "Only shopkeepers can record COD collection");
        Order order = findOrder(orderId);
        Shop shop = shopRepository.findById(order.getShopId())
                .orElseThrow(() -> new EntityNotFoundException("Shop not found: " + order.getShopId()));
        if (!shop.getOwnerId().equals(shopkeeperId)) {
            throw new EntityNotFoundException("Order not found: " + orderId);
        }

        Payment payment = findPayment(orderId);
        if (payment.getPaymentMethod() != PaymentMethod.CASH_ON_DELIVERY) {
            throw new IllegalStateException("Cash collection is only valid for COD orders");
        }
        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment is not pending");
        }

        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        order.setPaymentStatus(PaymentStatus.SUCCESS.name());
        orderRepository.save(order);
        return paymentRepository.save(payment);
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
    }

    private Payment findPayment(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found for order: " + orderId));
    }

    private void requireRole(Long userId, Role expectedRole, String errorMessage) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        if (!user.isEnabled() || user.getRole() != expectedRole) {
            throw new AccessDeniedException(errorMessage);
        }
    }
}
