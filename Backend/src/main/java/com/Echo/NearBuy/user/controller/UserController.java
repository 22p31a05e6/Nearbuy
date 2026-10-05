package com.Echo.NearBuy.user.controller;

import com.Echo.NearBuy.common.enums.Role;
import com.Echo.NearBuy.user.entity.User;
import com.Echo.NearBuy.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        return UserResponse.from(userService.findUserById(id));
    }

    @PutMapping("/{id}")
    public UserResponse updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        User user = userService.updateUserDetails(
                id,
                request.name(),
                request.email(),
                request.phone());
        return UserResponse.from(user);
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 255) String phone) {
    }

    public record UserResponse(
            Long id,
            String name,
            String email,
            String phone,
            Role role,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            boolean enabled) {
        private static UserResponse from(User user) {
            return new UserResponse(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getPhone(),
                    user.getRole(),
                    user.getCreatedAt(),
                    user.getUpdatedAt(),
                    user.isEnabled());
        }
    }
}
