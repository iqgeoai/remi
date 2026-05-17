package com.remi.lobby.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "game_players")
@IdClass(GamePlayerEntity.PK.class)
public class GamePlayerEntity {

  @Id @Column(name = "game_id") private UUID gameId;
  @Id @Column(name = "player_idx") private int playerIdx;

  @Column(name = "user_id", nullable = false) private UUID userId;

  @CreationTimestamp @Column(name = "joined_at", nullable = false, updatable = false)
  private Instant joinedAt;

  protected GamePlayerEntity() {}
  public GamePlayerEntity(UUID gameId, int playerIdx, UUID userId) {
    this.gameId = gameId; this.playerIdx = playerIdx; this.userId = userId;
  }

  public UUID getGameId() { return gameId; }
  public int getPlayerIdx() { return playerIdx; }
  public UUID getUserId() { return userId; }
  public Instant getJoinedAt() { return joinedAt; }

  public static class PK implements Serializable {
    private UUID gameId;
    private int playerIdx;
    public PK() {}
    public PK(UUID gameId, int playerIdx) { this.gameId = gameId; this.playerIdx = playerIdx; }
    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof PK pk)) return false;
      return playerIdx == pk.playerIdx && Objects.equals(gameId, pk.gameId);
    }
    @Override public int hashCode() { return Objects.hash(gameId, playerIdx); }
  }
}
