package com.Echo.NearBuy.auth.dto;

import com.Echo.NearBuy.common.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull Role role,
        @NotBlank @Pattern(regexp = "\\d{6}") String otp) {
}
