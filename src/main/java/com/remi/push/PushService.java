package com.remi.push;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PushService {
    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private final DeviceTokenRepository repo;

    public PushService(DeviceTokenRepository repo) {
        this.repo = repo;
    }

    public void notify(UUID userId, String title, String body, Map<String, String> data) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.debug("Firebase not initialized; would notify user {}: {}", userId, title);
            return;
        }
        List<DeviceToken> tokens = repo.findByUserId(userId);
        for (DeviceToken dt : tokens) {
            Message msg = Message.builder()
                .setToken(dt.getToken())
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data)
                .build();
            try {
                FirebaseMessaging.getInstance().send(msg);
            } catch (Exception e) {
                log.warn("Failed to send push to user {} token {}: {}", userId, dt.getToken(), e.getMessage());
            }
        }
    }
}
