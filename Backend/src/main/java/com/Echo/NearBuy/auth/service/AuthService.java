package com.Echo.NearBuy.auth.service;

import com.Echo.NearBuy.auth.dto.RegisterRequest;
import com.Echo.NearBuy.auth.dto.LoginRequest;
import com.Echo.NearBuy.auth.dto.AuthResponse;
import com.Echo.NearBuy.auth.dto.RefreshTokenRequest;
import com.Echo.NearBuy.auth.dto.VerifyEmailRequest;
import com.Echo.NearBuy.auth.entity.PendingRegistration;
import com.Echo.NearBuy.auth.repository.PendingRegistrationRepository;
import com.Echo.NearBuy.auth.security.JwtService;
import com.Echo.NearBuy.common.enums.Role;
import com.Echo.NearBuy.user.entity.User;
import com.Echo.NearBuy.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private static final Set<Role> SELF_REGISTERABLE_ROLES =
            Set.of(Role.CUSTOMER, Role.SHOPKEEPER, Role.DELIVERY_PERSON);
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final JwtService jwtService;

    public AuthService(
            PendingRegistrationRepository pendingRegistrationRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            JwtService jwtService,
            @Value("${app.mail.from}") String fromAddress) {
        this.pendingRegistrationRepository = pendingRegistrationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.jwtService = jwtService;
        this.fromAddress = fromAddress;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        var matchingUsers = userRepository.findAllByEmail(email).stream()
                .filter(User::isEnabled)
                .filter(user -> passwordEncoder.matches(request.password(), user.getPassword()))
                .filter(user -> request.role() == null || user.getRole() == request.role())
                .toList();

        if (matchingUsers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        if (matchingUsers.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Specify a role because this email has multiple matching accounts");
        }

        User user = matchingUsers.get(0);
        String accessToken = jwtService.generateToken(user.getId(), email, user.getRole(), "access");
        String refreshToken = jwtService.generateToken(user.getId(), email, user.getRole(), "refresh");
        return new AuthResponse(accessToken, refreshToken, user.getId(), user.getName(), user.getRole());
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();
        if (!jwtService.validateToken(refreshToken, "refresh")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }

        Long userId = jwtService.extractUserId(refreshToken);
        String email = jwtService.extractUsername(refreshToken);
        Role role = jwtService.extractRole(refreshToken);

        User user = userRepository.findById(userId)
                .filter(User::isEnabled)
                .filter(account -> account.getRole() == role)
                .filter(account -> normalizeEmail(account.getEmail()).equals(normalizeEmail(email)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole(), "access");
        String newRefreshToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole(), "refresh");
        return new AuthResponse(accessToken, newRefreshToken, user.getId(), user.getName(), user.getRole());
    }

    @Transactional
    public void requestRegistrationOtp(RegisterRequest request) {
        if (!SELF_REGISTERABLE_ROLES.contains(request.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This role cannot register publicly");
        }

        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailAndRole(email, request.role())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This email is already registered for this role");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        PendingRegistration pending = pendingRegistrationRepository
                .findByEmailAndRole(email, request.role())
                .orElseGet(PendingRegistration::new);

        if (pending.getId() != null && pending.getCreatedAt().isAfter(now.minusSeconds(60))) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Please wait before requesting another verification code");
        }

        String otp = String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
        pending.setName(request.name().strip());
        pending.setEmail(email);
        pending.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().strip());
        pending.setPasswordHash(passwordEncoder.encode(request.password()));
        pending.setRole(request.role());
        pending.setOtpHash(passwordEncoder.encode(otp));
        pending.setExpiresAt(now.plusMinutes(10));
        pending.setAttempts(0);
        pending.setCreatedAt(now);
        pendingRegistrationRepository.save(pending);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Verify your NearBuy email");
        message.setText("Your NearBuy verification code is " + otp + ". It expires in 10 minutes.");
        mailSender.send(message);
    }

    @Transactional
    public boolean verifyRegistrationEmail(VerifyEmailRequest request) {
        String email = normalizeEmail(request.email());
        PendingRegistration pending = pendingRegistrationRepository
                .findByEmailAndRole(email, request.role())
                .orElse(null);

        if (pending == null || pending.getAttempts() >= MAX_OTP_ATTEMPTS) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (pending.getExpiresAt().isBefore(now)) {
            pendingRegistrationRepository.delete(pending);
            return false;
        }

        if (!passwordEncoder.matches(request.otp(), pending.getOtpHash())) {
            pending.setAttempts(pending.getAttempts() + 1);
            pendingRegistrationRepository.save(pending);
            return false;
        }

        if (userRepository.existsByEmailAndRole(email, pending.getRole())) {
            pendingRegistrationRepository.delete(pending);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This email is already registered for this role");
        }

        User user = new User();
        user.setName(pending.getName());
        user.setEmail(pending.getEmail());
        user.setPhone(pending.getPhone());
        user.setPassword(pending.getPasswordHash());
        user.setRole(pending.getRole());
        userRepository.save(user);
        pendingRegistrationRepository.delete(pending);
        return true;
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
