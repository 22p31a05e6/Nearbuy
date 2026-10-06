package com.Echo.NearBuy.delivery.repository;

import com.Echo.NearBuy.delivery.entity.DeliveryPerson;
import com.Echo.NearBuy.delivery.enums.AvailabilityStatus;
import com.Echo.NearBuy.delivery.enums.VerificationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryPersonRepository extends JpaRepository<DeliveryPerson, Long> {
    Optional<DeliveryPerson> findByUserId(Long userId);

    List<DeliveryPerson> findByVerificationStatusAndAvailabilityStatus(
            VerificationStatus verificationStatus,
            AvailabilityStatus availabilityStatus);

    List<DeliveryPerson> findByVerificationStatus(VerificationStatus verificationStatus);
}
