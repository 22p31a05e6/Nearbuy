package com.Echo.NearBuy.auth.dto;

import com.Echo.NearBuy.common.enums.Role;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        Long userId,
        String name,
        Role role) {
}
