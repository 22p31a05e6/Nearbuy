package com.Echo.NearBuy.notification.service;

import com.Echo.NearBuy.common.enums.Role;
import com.Echo.NearBuy.notification.entity.Notification;
import com.Echo.NearBuy.notification.repository.NotificationRepository;
import com.Echo.NearBuy.user.entity.User;
import com.Echo.NearBuy.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(
            NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Notification notifyCustomer(Long customerId, String title, String message, String type) {
        return create(customerId, Role.CUSTOMER, title, message, type);
    }

    @Transactional
    public Notification notifyShopkeeper(Long shopkeeperId, String title, String message, String type) {
        return create(shopkeeperId, Role.SHOPKEEPER, title, message, type);
    }

    public List<Notification> getMyNotifications(Long userId) {
        requireEnabledUser(userId);
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public Notification markRead(Long userId, Long notificationId) {
        requireEnabledUser(userId);
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Notification not found: " + notificationId));
        if (!notification.getUserId().equals(userId)) {
            throw new EntityNotFoundException("Notification not found: " + notificationId);
        }
        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    private Notification create(
            Long recipientId, Role expectedRole, String title, String message, String type) {
        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Notification recipient not found: " + recipientId));
        if (recipient.getRole() != expectedRole) {
            throw new AccessDeniedException(
                    "Notification recipient must be a " + expectedRole.name().toLowerCase());
        }
        Notification notification = new Notification();
        notification.setUserId(recipientId);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        return notificationRepository.save(notification);
    }

    private User requireEnabledUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        if (!user.isEnabled()) {
            throw new AccessDeniedException("Disabled users cannot access notifications");
        }
        return user;
    }
}
