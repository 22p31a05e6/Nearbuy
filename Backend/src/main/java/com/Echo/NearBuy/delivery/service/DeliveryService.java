package com.Echo.NearBuy.delivery.service;

import com.Echo.NearBuy.common.enums.Role;
import com.Echo.NearBuy.delivery.dto.DeliveryLocationRequest;
import com.Echo.NearBuy.delivery.dto.DeliveryProfileRequest;
import com.Echo.NearBuy.delivery.entity.DeliveryPerson;
import com.Echo.NearBuy.delivery.enums.AvailabilityStatus;
import com.Echo.NearBuy.delivery.enums.VerificationStatus;
import com.Echo.NearBuy.delivery.repository.DeliveryPersonRepository;
import com.Echo.NearBuy.user.entity.User;
import com.Echo.NearBuy.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DeliveryService {
    private final DeliveryPersonRepository deliveryPersonRepository;
    private final UserRepository userRepository;

    public DeliveryService(
            DeliveryPersonRepository deliveryPersonRepository,
            UserRepository userRepository) {
        this.deliveryPersonRepository = deliveryPersonRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public DeliveryPerson register(Long userId, DeliveryProfileRequest request) {
        User user = findEnabledUser(userId);
        if (user.getRole() != Role.DELIVERY_PERSON) {
            throw new AccessDeniedException("Only delivery-person accounts can register a delivery profile");
        }
        if (deliveryPersonRepository.findByUserId(userId).isPresent()) {
            throw new IllegalStateException("Delivery profile already exists");
        }
        DeliveryPerson person = new DeliveryPerson();
        person.setUserId(userId);
        person.setVehicleType(request.vehicleType().strip());
        person.setVehicleNumber(request.vehicleNumber().strip().toUpperCase());
        person.setVerificationStatus(VerificationStatus.PENDING);
        person.setAvailabilityStatus(AvailabilityStatus.OFFLINE);
        person.setAvailable(false);
        person.setMaxDeliveryRangeKm(new BigDecimal("8.00"));
        return deliveryPersonRepository.save(person);
    }

    @Transactional
    public DeliveryPerson setAvailability(Long userId, AvailabilityStatus status) {
        DeliveryPerson person = requireProfile(userId);
        if (person.getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new IllegalStateException("Only approved delivery people can change availability");
        }
        if (person.getAvailabilityStatus() == AvailabilityStatus.BUSY
                && status != AvailabilityStatus.BUSY) {
            throw new IllegalStateException("Availability cannot be changed while an order is assigned");
        }
        if (status == AvailabilityStatus.BUSY) {
            throw new IllegalArgumentException("BUSY availability is controlled by order assignment");
        }
        if (status == AvailabilityStatus.ONLINE
                && (person.getCurrentLatitude() == null || person.getCurrentLongitude() == null)) {
            throw new IllegalStateException("Update your current location before going online");
        }
        person.setAvailabilityStatus(status);
        person.setAvailable(status == AvailabilityStatus.ONLINE);
        return deliveryPersonRepository.save(person);
    }

    @Transactional
    public DeliveryPerson updateRange(Long userId, BigDecimal rangeKm) {
        DeliveryPerson person = requireProfile(userId);
        if (person.getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new IllegalStateException("Only approved delivery people can change their delivery range");
        }
        if (rangeKm == null
                || rangeKm.compareTo(new BigDecimal("0.1")) < 0
                || rangeKm.compareTo(new BigDecimal("20.0")) > 0) {
            throw new IllegalArgumentException("Delivery range must be between 0.1 and 20 km");
        }
        person.setMaxDeliveryRangeKm(rangeKm);
        return deliveryPersonRepository.save(person);
    }

    @Transactional
    public DeliveryPerson updateLocation(Long userId, DeliveryLocationRequest request) {
        DeliveryPerson person = requireProfile(userId);
        person.setCurrentLatitude(request.latitude());
        person.setCurrentLongitude(request.longitude());
        return deliveryPersonRepository.save(person);
    }

    @Transactional
    public DeliveryPerson approve(Long adminId, Long deliveryPersonId) {
        requireAdmin(adminId);
        DeliveryPerson person = deliveryPersonRepository.findById(deliveryPersonId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Delivery profile not found: " + deliveryPersonId));
        person.setVerificationStatus(VerificationStatus.APPROVED);
        return deliveryPersonRepository.save(person);
    }

    @Transactional
    public DeliveryPerson reject(Long adminId, Long deliveryPersonId) {
        requireAdmin(adminId);
        DeliveryPerson person = deliveryPersonRepository.findById(deliveryPersonId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Delivery profile not found: " + deliveryPersonId));
        person.setVerificationStatus(VerificationStatus.REJECTED);
        person.setAvailabilityStatus(AvailabilityStatus.OFFLINE);
        person.setAvailable(false);
        return deliveryPersonRepository.save(person);
    }

    public java.util.List<DeliveryPerson> getPendingProfiles(Long adminId) {
        requireAdmin(adminId);
        return deliveryPersonRepository.findByVerificationStatus(VerificationStatus.PENDING);
    }

    private DeliveryPerson requireProfile(Long userId) {
        User user = findEnabledUser(userId);
        if (user.getRole() != Role.DELIVERY_PERSON) {
            throw new AccessDeniedException("Only delivery-person accounts can manage delivery availability");
        }
        return deliveryPersonRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Delivery profile not found for user: " + userId));
    }

    private User findEnabledUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        if (!user.isEnabled()) {
            throw new AccessDeniedException("Disabled users cannot manage delivery profiles");
        }
        return user;
    }

    private void requireAdmin(Long adminId) {
        User admin = findEnabledUser(adminId);
        if (admin.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only an admin can verify delivery profiles");
        }
    }
}
