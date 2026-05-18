package com.remi.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;

@Configuration
public class FirebaseConfig {
    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @PostConstruct
    void init() {
        String credPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (credPath == null || credPath.isBlank()) {
            log.warn("GOOGLE_APPLICATION_CREDENTIALS not set; push notifications disabled.");
            return;
        }
        try {
            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(new FileInputStream(credPath)))
                .build();
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            log.info("Firebase Admin SDK initialized.");
        } catch (Exception e) {
            log.error("Failed to initialize Firebase: {}", e.getMessage());
        }
    }
}
