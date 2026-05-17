package com.remi.persistence;

import com.remi.engine.domain.GameState;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "games")
public class GameEntity {
  @Id
  private UUID id;

  @Type(JsonBinaryType.class)
  @Column(name = "state", columnDefinition = "jsonb", nullable = false)
  private GameState state;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "owner_id")
  private java.util.UUID ownerId;

  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  @Column(name = "visibility", nullable = false, length = 10)
  private com.remi.lobby.domain.GameVisibility visibility = com.remi.lobby.domain.GameVisibility.PRIVATE;

  @Column(name = "join_code", unique = true, length = 8)
  private String joinCode;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected GameEntity() {}
  public GameEntity(UUID id, GameState state) { this.id = id; this.state = state; }

  public UUID getId() { return id; }
  public GameState getState() { return state; }
  public void setState(GameState state) { this.state = state; }
  public Long getVersion() { return version; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public java.util.UUID getOwnerId() { return ownerId; }
  public void setOwnerId(java.util.UUID ownerId) { this.ownerId = ownerId; }
  public com.remi.lobby.domain.GameVisibility getVisibility() { return visibility; }
  public void setVisibility(com.remi.lobby.domain.GameVisibility visibility) { this.visibility = visibility; }
  public String getJoinCode() { return joinCode; }
  public void setJoinCode(String joinCode) { this.joinCode = joinCode; }
}
