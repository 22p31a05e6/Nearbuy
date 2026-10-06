package com.Echo.NearBuy.payment.service;

import com.Echo.NearBuy.common.enums.PaymentMethod;
import com.Echo.NearBuy.common.enums.PaymentPurpose;
import com.Echo.NearBuy.common.enums.PaymentStatus;
import com.Echo.NearBuy.common.enums.Role;
import com.Echo.NearBuy.payment.dto.RazorpayCheckoutResponse;
import com.Echo.NearBuy.payment.dto.VerifyRazorpayPaymentRequest;
import com.Echo.NearBuy.order.entity.Order;
import com.Echo.NearBuy.order.repository.OrderRepository;
import com.Echo.NearBuy.delivery.repository.DeliveryAssignmentRepository;
import com.Echo.NearBuy.delivery.enums.DeliveryStatus;
import com.Echo.NearBuy.delivery.service.DeliveryAssignmentService;
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
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final DeliveryAssignmentService deliveryAssignmentService;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            ShopRepository shopRepository,
            UserRepository userRepository,
            RazorpayGatewayService razorpayGatewayService,
            DeliveryAssignmentRepository deliveryAssignmentRepository,
            DeliveryAssignmentService deliveryAssignmentService) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.razorpayGatewayService = razorpayGatewayService;
        this.deliveryAssignmentRepository = deliveryAssignmentRepository;
        this.deliveryAssignmentService = deliveryAssignmentService;
    }

    @Transactional
    public Payment createPendingPayment(Order order, PaymentMethod paymentMethod) {
        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setAmount(order.getTotalAmount());
        payment.setPaymentMethod(paymentMethod);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setPurpose(PaymentPurpose.ORDER);
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

    public boolean hasActiveRazorpayOrder(Long orderId) {
        return paymentRepository.findFirstByOrderIdAndPurposeOrderByCreatedAtAsc(
                        orderId, PaymentPurpose.ORDER)
                .or(() -> paymentRepository.findFirstByOrderIdAndPurposeIsNullOrderByCreatedAtAsc(orderId))
                .filter(payment -> payment.getPaymentMethod() == PaymentMethod.ONLINE)
                .map(payment -> payment.getGatewayOrderId() != null
                        && !payment.getGatewayOrderId().isBlank())
                .orElse(false);
    }

    @Transactional
    public RazorpayCheckoutResponse createRazorpayOrder(Long customerId, Long orderId) {
        requireRole(customerId, Role.CUSTOMER, "Only customers can initiate online payments");
        Order order = findOrder(orderId);
        if (!order.getCustomerId().equals(customerId)) {
            throw new EntityNotFoundException("Order not found: " + orderId);
        }
        Payment payment = findPayment(orderId);
        return createRazorpayCheckout(order, payment);
    }

    @Transactional
    public Payment createDeliveryFeeAdjustment(Long customerId, Long orderId, java.math.BigDecimal newDeliveryFee) {
        requireRole(customerId, Role.CUSTOMER, "Only customers can change delivery offers");
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
        if (!order.getCustomerId().equals(customerId)) {
            throw new EntityNotFoundException("Order not found: " + orderId);
        }
        deliveryAssignmentService.markUnavailableIfNoEligiblePerson(orderId);
        if (!"DELIVERY_UNAVAILABLE".equals(order.getStatus())) {
            throw new IllegalStateException("Delivery offer can only be increased after the request expires");
        }
        if (order.getPaymentMethod() == null || !PaymentMethod.ONLINE.name().equals(order.getPaymentMethod())
                || !PaymentStatus.SUCCESS.name().equals(order.getPaymentStatus())) {
            throw new IllegalStateException("Order must be paid online before changing the delivery offer");
        }
        if (newDeliveryFee == null || newDeliveryFee.compareTo(order.getDeliveryFee()) <= 0) {
            throw new IllegalArgumentException("New delivery offer must exceed the current delivery fee");
        }
        if (paymentRepository.existsByOrderIdAndPurposeAndPaymentStatus(
                orderId, PaymentPurpose.DELIVERY_FEE_ADJUSTMENT, PaymentStatus.PENDING)) {
            throw new IllegalStateException("A delivery offer increase payment is already pending");
        }
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(newDeliveryFee.subtract(order.getDeliveryFee()));
        payment.setPaymentMethod(PaymentMethod.ONLINE);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setPurpose(PaymentPurpose.DELIVERY_FEE_ADJUSTMENT);
        return paymentRepository.save(payment);
    }

    @Transactional
    public RazorpayCheckoutResponse createDeliveryFeeAdjustmentCheckout(
            Long customerId, Long orderId, Long paymentId) {
        requireRole(customerId, Role.CUSTOMER, "Only customers can initiate delivery payments");
        Order order = findOrder(orderId);
        if (!order.getCustomerId().equals(customerId)) {
            throw new EntityNotFoundException("Order not found: " + orderId);
        }
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));
        if (!payment.getOrderId().equals(orderId)
                || payment.getPurpose() != PaymentPurpose.DELIVERY_FEE_ADJUSTMENT) {
            throw new EntityNotFoundException("Delivery payment not found: " + paymentId);
        }
        return createRazorpayCheckout(order, payment);
    }

    private RazorpayCheckoutResponse createRazorpayCheckout(Order order, Payment payment) {
        if (payment.getPaymentMethod() != PaymentMethod.ONLINE) {
            throw new IllegalStateException("Razorpay is only available for ONLINE payments");
        }
        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment is not pending");
        }

        String gatewayOrderId = payment.getGatewayOrderId();
        if (gatewayOrderId == null || gatewayOrderId.isBlank()) {
            RazorpayGatewayService.GatewayOrder gatewayOrder =
                    razorpayGatewayService.createOrder(payment.getId(), payment.getAmount());
            gatewayOrderId = gatewayOrder.id();
            payment.setGatewayOrderId(gatewayOrderId);
            paymentRepository.save(payment);
        }

        return new RazorpayCheckoutResponse(
                order.getId(),
                payment.getId(),
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
        verifyRazorpayPayment(customerId, order, payment, request);
        return payment;
    }

    @Transactional
    public Payment verifyDeliveryFeeAdjustmentPayment(
            Long customerId,
            Long orderId,
            Long paymentId,
            VerifyRazorpayPaymentRequest request) {
        requireRole(customerId, Role.CUSTOMER, "Only customers can verify their payments");
        Order order = findOrder(orderId);
        if (!order.getCustomerId().equals(customerId)) {
            throw new EntityNotFoundException("Order not found: " + orderId);
        }
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));
        if (!payment.getOrderId().equals(orderId)
                || payment.getPurpose() != PaymentPurpose.DELIVERY_FEE_ADJUSTMENT) {
            throw new EntityNotFoundException("Delivery payment not found: " + paymentId);
        }
        verifyRazorpayPayment(customerId, order, payment, request);
        order.setDeliveryFee(order.getDeliveryFee().add(payment.getAmount()));
        order.setTotalAmount(order.getTotalAmount().add(payment.getAmount()));
        order.setStatus(com.Echo.NearBuy.common.enums.OrderStatus.DELIVERY_REQUESTED.name());
        orderRepository.save(order);
        deliveryAssignmentRepository.deleteByOrderIdAndStatus(orderId, DeliveryStatus.REJECTED);
        return payment;
    }

    private void verifyRazorpayPayment(
            Long customerId,
            Order order,
            Payment payment,
            VerifyRazorpayPaymentRequest request) {
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
        if (payment.getPurpose() == PaymentPurpose.ORDER) {
            order.setPaymentStatus(PaymentStatus.SUCCESS.name());
            orderRepository.save(order);
        }
        paymentRepository.save(payment);
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
        return paymentRepository.findFirstByOrderIdAndPurposeOrderByCreatedAtAsc(
                        orderId, PaymentPurpose.ORDER)
                .or(() -> paymentRepository.findFirstByOrderIdAndPurposeIsNullOrderByCreatedAtAsc(orderId))
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
