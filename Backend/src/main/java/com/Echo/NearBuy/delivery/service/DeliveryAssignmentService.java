package com.Echo.NearBuy.delivery.service;

import com.Echo.NearBuy.common.enums.OrderStatus;
import com.Echo.NearBuy.common.enums.PaymentStatus;
import com.Echo.NearBuy.common.enums.Role;
import com.Echo.NearBuy.delivery.dto.DeliveryRequestResponse;
import com.Echo.NearBuy.delivery.entity.DeliveryAssignment;
import com.Echo.NearBuy.delivery.entity.DeliveryPerson;
import com.Echo.NearBuy.delivery.enums.AvailabilityStatus;
import com.Echo.NearBuy.delivery.enums.DeliveryStatus;
import com.Echo.NearBuy.delivery.enums.VerificationStatus;
import com.Echo.NearBuy.delivery.repository.DeliveryAssignmentRepository;
import com.Echo.NearBuy.delivery.repository.DeliveryPersonRepository;
import com.Echo.NearBuy.location.service.LocationService;
import com.Echo.NearBuy.notification.service.NotificationService;
import com.Echo.NearBuy.order.entity.Order;
import com.Echo.NearBuy.order.repository.OrderRepository;
import com.Echo.NearBuy.shop.entity.Shop;
import com.Echo.NearBuy.shop.repository.ShopRepository;
import com.Echo.NearBuy.user.entity.User;
import com.Echo.NearBuy.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DeliveryAssignmentService {
    private final DeliveryPersonRepository deliveryPersonRepository;
    private final DeliveryAssignmentRepository assignmentRepository;
    private final OrderRepository orderRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final LocationService locationService;
    private final NotificationService notificationService;

    public DeliveryAssignmentService(
            DeliveryPersonRepository deliveryPersonRepository,
            DeliveryAssignmentRepository assignmentRepository,
            OrderRepository orderRepository,
            ShopRepository shopRepository,
            UserRepository userRepository,
            LocationService locationService,
            NotificationService notificationService) {
        this.deliveryPersonRepository = deliveryPersonRepository;
        this.assignmentRepository = assignmentRepository;
        this.orderRepository = orderRepository;
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.locationService = locationService;
        this.notificationService = notificationService;
    }

    @Transactional
    public List<DeliveryRequestResponse> getAvailableOrders(Long userId) {
        DeliveryPerson person = requireOnlinePerson(userId);
        List<Order> requestedOrders = orderRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(OrderStatus.DELIVERY_REQUESTED.name()));
        List<DeliveryRequestResponse> requests = requestedOrders.stream()
                .filter(order -> PaymentStatus.SUCCESS.name().equals(order.getPaymentStatus()))
                .filter(order -> !assignmentRepository
                        .existsByOrderIdAndStatus(order.getId(), DeliveryStatus.ACCEPTED))
                .filter(order -> assignmentRepository
                        .findByOrderIdAndDeliveryPersonId(order.getId(), person.getId())
                        .map(assignment -> assignment.getStatus() != DeliveryStatus.REJECTED)
                        .orElse(true))
                .map(order -> toRequestResponse(person, order))
                .filter(java.util.Objects::nonNull)
                .toList();
        for (Order order : requestedOrders) {
            if (PaymentStatus.SUCCESS.name().equals(order.getPaymentStatus())
                    && !hasUnrejectedEligiblePerson(order)) {
                order.setStatus(OrderStatus.DELIVERY_UNAVAILABLE.name());
                orderRepository.save(order);
            }
        }
        return requests;
    }

    @Transactional
    public void markUnavailableIfNoEligiblePerson(Long orderId) {
        Order order = lockOrder(orderId);
        if (OrderStatus.DELIVERY_REQUESTED.name().equals(order.getStatus())
                && !hasUnrejectedEligiblePerson(order)) {
            order.setStatus(OrderStatus.DELIVERY_UNAVAILABLE.name());
            orderRepository.save(order);
        }
    }

    @Transactional
    public DeliveryAssignment acceptOrder(Long userId, Long orderId) {
        DeliveryPerson person = requireOnlinePerson(userId);
        Order order = lockOrder(orderId);
        requireRequestable(order);
        if (assignmentRepository.existsByOrderIdAndStatus(orderId, DeliveryStatus.ACCEPTED)) {
            throw new IllegalStateException("Another delivery person has already accepted this order");
        }
        if (toRequestResponse(person, order) == null) {
            throw new IllegalStateException("Order is outside your selected delivery range");
        }

        DeliveryAssignment assignment = assignmentRepository
                .findByOrderIdAndDeliveryPersonId(orderId, person.getId())
                .orElseGet(() -> {
                    DeliveryAssignment newAssignment = new DeliveryAssignment();
                    newAssignment.setOrderId(orderId);
                    newAssignment.setDeliveryPersonId(person.getId());
                    return newAssignment;
                });
        if (assignment.getStatus() == DeliveryStatus.REJECTED) {
            throw new IllegalStateException("You already rejected this delivery request");
        }
        assignment.setStatus(DeliveryStatus.ACCEPTED);
        order.setStatus(OrderStatus.DELIVERY_ACCEPTED.name());
        person.setAvailabilityStatus(AvailabilityStatus.BUSY);
        person.setAvailable(false);
        orderRepository.save(order);
        deliveryPersonRepository.save(person);
        DeliveryAssignment savedAssignment = assignmentRepository.save(assignment);
        Shop shop = shopRepository.findById(order.getShopId())
                .orElseThrow(() -> new EntityNotFoundException("Shop not found: " + order.getShopId()));
        notificationService.notifyShopkeeper(
                shop.getOwnerId(),
                "Delivery person assigned",
                "A delivery person accepted order #" + order.getId() + ".",
                "DELIVERY_ASSIGNED");
        notificationService.notifyCustomer(
                order.getCustomerId(),
                "Delivery person assigned",
                "A delivery person is assigned to order #" + order.getId() + ".",
                "DELIVERY_ASSIGNED");
        return savedAssignment;
    }

    @Transactional
    public DeliveryAssignment rejectOrder(Long userId, Long orderId) {
        DeliveryPerson person = requireOnlinePerson(userId);
        Order order = lockOrder(orderId);
        requireRequestable(order);
        if (assignmentRepository.existsByOrderIdAndStatus(orderId, DeliveryStatus.ACCEPTED)) {
            throw new IllegalStateException("Order has already been accepted by another delivery person");
        }
        DeliveryAssignment assignment = assignmentRepository
                .findByOrderIdAndDeliveryPersonId(orderId, person.getId())
                .orElseGet(() -> {
                    DeliveryAssignment newAssignment = new DeliveryAssignment();
                    newAssignment.setOrderId(orderId);
                    newAssignment.setDeliveryPersonId(person.getId());
                    return newAssignment;
                });
        if (assignment.getStatus() == DeliveryStatus.ACCEPTED) {
            throw new IllegalStateException("An accepted delivery cannot be rejected");
        }
        assignment.setStatus(DeliveryStatus.REJECTED);
        DeliveryAssignment savedAssignment = assignmentRepository.save(assignment);
        if (!hasUnrejectedEligiblePerson(order)) {
            order.setStatus(OrderStatus.DELIVERY_UNAVAILABLE.name());
            orderRepository.save(order);
        }
        return savedAssignment;
    }

    @Transactional
    public DeliveryAssignment markPickedUp(Long userId, Long orderId) {
        DeliveryPerson person = requireAssignedPerson(userId, orderId);
        DeliveryAssignment assignment = getActiveAssignment(person.getId(), orderId);
        Order order = lockOrder(orderId);
        if (!OrderStatus.DELIVERY_ACCEPTED.name().equals(order.getStatus())) {
            throw new IllegalStateException("Order is not ready for pickup");
        }
        assignment.setStatus(DeliveryStatus.PICKED_UP);
        assignment.setPickedUpAt(LocalDateTime.now());
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY.name());
        orderRepository.save(order);
        notificationService.notifyCustomer(
                order.getCustomerId(),
                "Order picked up",
                "Your order #" + order.getId() + " has been picked up and is on its way.",
                "ORDER_PICKED_UP");
        return assignmentRepository.save(assignment);
    }

    @Transactional
    public DeliveryAssignment markDelivered(Long userId, Long orderId) {
        DeliveryPerson person = requireAssignedPerson(userId, orderId);
        DeliveryAssignment assignment = getActiveAssignment(person.getId(), orderId);
        Order order = lockOrder(orderId);
        if (!OrderStatus.PICKED_UP.name().equals(order.getStatus())
                && !OrderStatus.OUT_FOR_DELIVERY.name().equals(order.getStatus())) {
            throw new IllegalStateException("Order must be picked up before delivery completion");
        }
        assignment.setStatus(DeliveryStatus.DELIVERED);
        assignment.setDeliveredAt(LocalDateTime.now());
        order.setStatus(OrderStatus.DELIVERED.name());
        person.setAvailabilityStatus(AvailabilityStatus.ONLINE);
        person.setAvailable(true);
        orderRepository.save(order);
        deliveryPersonRepository.save(person);
        notificationService.notifyCustomer(
                order.getCustomerId(),
                "Order delivered",
                "Your order #" + order.getId() + " has been delivered.",
                "ORDER_DELIVERED");
        return assignmentRepository.save(assignment);
    }

    private DeliveryRequestResponse toRequestResponse(DeliveryPerson person, Order order) {
        Shop shop = shopRepository.findById(order.getShopId())
                .orElseThrow(() -> new EntityNotFoundException("Shop not found: " + order.getShopId()));
        if (shop.getLatitude() == null || shop.getLongitude() == null
                || order.getLatitude() == null || order.getLongitude() == null) {
            throw new IllegalStateException("Shop or customer location is not set for this order");
        }
        LocationService.RouteResult routeToShop = locationService.computeRoute(
                person.getCurrentLatitude(), person.getCurrentLongitude(),
                shop.getLatitude(), shop.getLongitude());
        BigDecimal distanceToShopKm = metersToKm(routeToShop.distanceMeters());
        if (distanceToShopKm.compareTo(person.getMaxDeliveryRangeKm()) > 0) {
            return null;
        }
        LocationService.RouteResult routeToCustomer = locationService.computeRoute(
                shop.getLatitude(), shop.getLongitude(), order.getLatitude(), order.getLongitude());
        BigDecimal shopToCustomerKm = metersToKm(routeToCustomer.distanceMeters());
        BigDecimal totalDistanceKm = metersToKm(
                Math.addExact(routeToShop.distanceMeters(), routeToCustomer.distanceMeters()));
        User customer = userRepository.findById(order.getCustomerId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Customer not found: " + order.getCustomerId()));
        BigDecimal offeredPrice = order.getDeliveryFee();
        return new DeliveryRequestResponse(
                order.getId(),
                shop.getShopName(),
                customer.getName(),
                order.getDeliveryAddress(),
                distanceToShopKm,
                shopToCustomerKm,
                totalDistanceKm,
                offeredPrice,
                offeredPrice,
                totalDistanceKm.signum() == 0
                        ? BigDecimal.ZERO
                        : offeredPrice.divide(totalDistanceKm, 2, RoundingMode.HALF_UP));
    }

    private BigDecimal metersToKm(long meters) {
        return BigDecimal.valueOf(meters).divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
    }

    private boolean hasUnrejectedEligiblePerson(Order order) {
        Shop shop = shopRepository.findById(order.getShopId())
                .orElseThrow(() -> new EntityNotFoundException("Shop not found: " + order.getShopId()));
        if (shop.getLatitude() == null || shop.getLongitude() == null) {
            throw new IllegalStateException("Shop location is not set for delivery routing");
        }
        List<DeliveryPerson> onlinePersons = deliveryPersonRepository
                .findByVerificationStatusAndAvailabilityStatus(
                        VerificationStatus.APPROVED, AvailabilityStatus.ONLINE);
        for (DeliveryPerson person : onlinePersons) {
            if (person.getCurrentLatitude() == null || person.getCurrentLongitude() == null) {
                continue;
            }
            BigDecimal distance = metersToKm(locationService.computeRoute(
                    person.getCurrentLatitude(), person.getCurrentLongitude(),
                    shop.getLatitude(), shop.getLongitude()).distanceMeters());
            if (distance.compareTo(person.getMaxDeliveryRangeKm()) > 0) {
                continue;
            }
            DeliveryAssignment previousResponse = assignmentRepository
                    .findByOrderIdAndDeliveryPersonId(order.getId(), person.getId())
                    .orElse(null);
            if (previousResponse == null || previousResponse.getStatus() != DeliveryStatus.REJECTED) {
                return true;
            }
        }
        return false;
    }

    private Order lockOrder(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
    }

    private void requireRequestable(Order order) {
        if (!OrderStatus.DELIVERY_REQUESTED.name().equals(order.getStatus())
                || !PaymentStatus.SUCCESS.name().equals(order.getPaymentStatus())) {
            throw new IllegalStateException("Order is not available for delivery");
        }
    }

    private DeliveryPerson requireOnlinePerson(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        if (!user.isEnabled() || user.getRole() != Role.DELIVERY_PERSON) {
            throw new AccessDeniedException("Only enabled delivery people can access delivery orders");
        }
        DeliveryPerson person = deliveryPersonRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Delivery profile not found"));
        if (person.getVerificationStatus() != VerificationStatus.APPROVED
                || person.getAvailabilityStatus() != AvailabilityStatus.ONLINE) {
            throw new AccessDeniedException("Delivery profile must be approved and online");
        }
        if (person.getCurrentLatitude() == null || person.getCurrentLongitude() == null) {
            throw new IllegalStateException("Update your current location before viewing delivery orders");
        }
        return person;
    }

    private DeliveryPerson requireAssignedPerson(Long userId, Long orderId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        if (!user.isEnabled() || user.getRole() != Role.DELIVERY_PERSON) {
            throw new AccessDeniedException("Only enabled delivery people can update delivery status");
        }
        DeliveryPerson person = deliveryPersonRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Delivery profile not found"));
        if (person.getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new AccessDeniedException("Delivery profile must be approved");
        }
        getActiveAssignment(person.getId(), orderId);
        return person;
    }

    private DeliveryAssignment getActiveAssignment(Long personId, Long orderId) {
        DeliveryAssignment assignment = assignmentRepository
                .findByOrderIdAndDeliveryPersonId(orderId, personId)
                .orElseThrow(() -> new EntityNotFoundException("Delivery assignment not found"));
        if (assignment.getStatus() != DeliveryStatus.ACCEPTED
                && assignment.getStatus() != DeliveryStatus.PICKED_UP) {
            throw new IllegalStateException("Delivery assignment is not active");
        }
        return assignment;
    }
}
