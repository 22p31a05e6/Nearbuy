package com.Echo.NearBuy.user.service;

import com.Echo.NearBuy.user.entity.User;
import com.Echo.NearBuy.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Objects;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User createUser(User user) {
        return userRepository.save(Objects.requireNonNull(user, "user must not be null"));
    }

    public User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }

    public List<User> findUsersByEmail(String email) {
        return userRepository.findAllByEmail(email);
    }

    @Transactional
    public User updateUser(Long id, User updatedUser) {
        Objects.requireNonNull(updatedUser, "updatedUser must not be null");

        User user = findUserById(id);
        user.setName(updatedUser.getName());
        user.setEmail(updatedUser.getEmail());
        user.setPhone(updatedUser.getPhone());
        user.setPassword(updatedUser.getPassword());
        user.setRole(updatedUser.getRole());

        return userRepository.save(user);
    }

    @Transactional
    public User updateUserDetails(Long id, String name, String email, String phone) {
        User user = findUserById(id);
        user.setName(name);
        user.setEmail(email);
        user.setPhone(phone);
        return userRepository.save(user);
    }

    @Transactional
    public User disableUser(Long id) {
        User user = findUserById(id);
        user.setEnabled(false);
        return userRepository.save(user);
    }
}
