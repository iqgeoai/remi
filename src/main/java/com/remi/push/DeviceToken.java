package com.remi.push;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_tokens", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "token"}))
public class DeviceToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 512)
    private String token;

    @Column(nullable = false, length = 16)
    private String platform;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected DeviceToken() {}

    public DeviceToken(UUID userId, String token, String platform) {
        this.userId = userId;
        this.token = token;
        this.platform = platform;
    }

    public Long getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getToken() { return token; }
    public String getPlatform() { return platform; }
    public Instant getCreatedAt() { return createdAt; }
}
