package com.Echo.NearBuy.notification.controller;

import com.Echo.NearBuy.notification.entity.Notification;
import com.Echo.NearBuy.notification.service.NotificationService;
import com.Echo.NearBuy.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/my")
    public List<NotificationResponse> getMyNotifications(
            @AuthenticationPrincipal User authenticatedUser) {
        return notificationService.getMyNotifications(authenticatedUser.getId()).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationResponse markRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal User authenticatedUser) {
        return NotificationResponse.from(
                notificationService.markRead(authenticatedUser.getId(), notificationId));
    }

    public record NotificationResponse(
            Long id,
            Long userId,
            String title,
            String message,
            String type,
            boolean isRead,
            LocalDateTime createdAt) {
        private static NotificationResponse from(Notification notification) {
            return new NotificationResponse(
                    notification.getId(),
                    notification.getUserId(),
                    notification.getTitle(),
                    notification.getMessage(),
                    notification.getType(),
                    notification.isRead(),
                    notification.getCreatedAt());
        }
    }
}
