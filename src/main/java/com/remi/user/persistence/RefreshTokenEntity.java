package com.remi.user.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {
  @Id private UUID id;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;
  @Column(name = "revoked_at") private Instant revokedAt;
  @Column(name = "replaced_by") private UUID replacedBy;
  @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

  protected RefreshTokenEntity() {}
  public RefreshTokenEntity(UUID id, UUID userId, Instant expiresAt) {
    this.id = id; this.userId = userId; this.expiresAt = expiresAt;
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public Instant getExpiresAt() { return expiresAt; }
  public Instant getRevokedAt() { return revokedAt; }
  public UUID getReplacedBy() { return replacedBy; }
  public Instant getCreatedAt() { return createdAt; }

  public void revoke(Instant when) { this.revokedAt = when; }
  public void rotate(Instant when, UUID newId) { this.revokedAt = when; this.replacedBy = newId; }
}
