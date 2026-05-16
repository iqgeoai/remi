package com.remi.user.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_tokens")
public class VerificationTokenEntity {
  @Id private UUID id;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;
  @Column(name = "used_at") private Instant usedAt;
  @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

  protected VerificationTokenEntity() {}
  public VerificationTokenEntity(UUID id, UUID userId, Instant expiresAt) {
    this.id = id; this.userId = userId; this.expiresAt = expiresAt;
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public Instant getExpiresAt() { return expiresAt; }
  public Instant getUsedAt() { return usedAt; }
  public void markUsed(Instant when) { this.usedAt = when; }
}
