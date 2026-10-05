package com.Echo.NearBuy.auth.repository;

import com.Echo.NearBuy.auth.entity.PendingRegistration;
import com.Echo.NearBuy.common.enums.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {
    Optional<PendingRegistration> findByEmailAndRole(String email, Role role);
}
