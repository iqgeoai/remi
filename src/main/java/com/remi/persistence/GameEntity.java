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
}
