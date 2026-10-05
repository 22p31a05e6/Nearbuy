package com.Echo.NearBuy.auth.dto;

import com.Echo.NearBuy.common.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 255) String phone,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull Role role) {
}
