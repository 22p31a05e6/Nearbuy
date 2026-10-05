package com.Echo.NearBuy.user.repository;

import com.Echo.NearBuy.user.entity.User;
import com.Echo.NearBuy.common.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findAllByEmail(String email);

    Optional<User> findByEmailAndRole(String email, Role role);

    boolean existsByEmailAndRole(String email, Role role);

    boolean existsByPhone(String phone);
}
