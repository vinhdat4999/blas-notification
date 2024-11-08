package com.blas.blasnotification.controller;

import static com.blas.blascommon.security.SecurityUtils.getUsernameLoggedIn;

import com.blas.blascommon.core.model.Notification;
import com.blas.blascommon.core.service.NotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class NotificationController {

  @Lazy
  protected final NotificationService notificationService;

  @GetMapping(value = "/notifications")
  public ResponseEntity<List<Notification>> getNotificationsByUsername() {
    List<Notification> notifications = getNotificationsDescByTime();
    return ResponseEntity.ok(notifications);
  }

  @PostMapping(value = "/mark-as-read/{notificationId}")
  public ResponseEntity<List<Notification>> markAsRead(
      @PathVariable(value = "notificationId") String notificationId) {
    notificationService.updateReadNotification(notificationId);
    List<Notification> notifications = getNotificationsDescByTime();
    return ResponseEntity.ok(notifications);
  }

  @PostMapping(value = "/mark-as-read-all")
  public ResponseEntity<List<Notification>> markAsReadAll() {
    List<Notification> notifications = notificationService.getAllNotificationByUsername(
        getUsernameLoggedIn());
    notifications.forEach(notification -> {
      if (!notification.isRead()) {
        notification.setRead(true);
        notificationService.updateReadNotification(notification.getNotificationId());
      }
    });
    return ResponseEntity.ok(notifications);
  }

  private List<Notification> getNotificationsDescByTime() {
    List<Notification> notifications = notificationService.getAllNotificationByUsername(
        getUsernameLoggedIn());
    notifications.sort((n1, n2) -> n2.getCreatedTime().compareTo(n1.getCreatedTime()));
    return notifications;
  }
}
