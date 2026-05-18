package com.remi.friends;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_blocks")
public class UserBlock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_id", nullable = false)
    private UUID blockerId;

    @Column(name = "blocked_id", nullable = false)
    private UUID blockedId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected UserBlock() {}

    public UserBlock(UUID blockerId, UUID blockedId) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
    }

    public Long getId() { return id; }
    public UUID getBlockerId() { return blockerId; }
    public UUID getBlockedId() { return blockedId; }
    public Instant getCreatedAt() { return createdAt; }
}
