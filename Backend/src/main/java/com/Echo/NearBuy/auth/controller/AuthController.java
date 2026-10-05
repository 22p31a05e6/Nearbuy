package com.Echo.NearBuy.auth.controller;

import com.Echo.NearBuy.auth.dto.RegisterRequest;
import com.Echo.NearBuy.auth.dto.LoginRequest;
import com.Echo.NearBuy.auth.dto.AuthResponse;
import com.Echo.NearBuy.auth.dto.RefreshTokenRequest;
import com.Echo.NearBuy.auth.dto.VerifyEmailRequest;
import com.Echo.NearBuy.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        authService.requestRegistrationOtp(request);
        return ResponseEntity.accepted()
                .body(new MessageResponse("Verification code sent. Verify your email to complete registration."));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        if (!authService.verifyRegistrationEmail(request)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code");
        }
        return new MessageResponse("Email verified and account created.");
    }

    public record MessageResponse(String message) {
    }
}
