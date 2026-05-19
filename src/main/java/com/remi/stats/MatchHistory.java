package com.remi.stats;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_history")
public class MatchHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false, unique = true)
    private UUID gameId;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt = Instant.now();

    @Column(name = "duration_sec", nullable = false)
    private int durationSec;

    @Column(name = "winner_id", nullable = false)
    private UUID winnerId;

    protected MatchHistory() {}

    public MatchHistory(UUID gameId, int durationSec, UUID winnerId) {
        this.gameId = gameId;
        this.durationSec = durationSec;
        this.winnerId = winnerId;
    }

    public Long getId() { return id; }
    public UUID getGameId() { return gameId; }
    public Instant getFinishedAt() { return finishedAt; }
    public int getDurationSec() { return durationSec; }
    public UUID getWinnerId() { return winnerId; }
}
