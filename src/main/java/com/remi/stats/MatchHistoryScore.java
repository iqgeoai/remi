package com.remi.stats;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "match_history_score", uniqueConstraints = @UniqueConstraint(columnNames = {"history_id", "user_id"}))
public class MatchHistoryScore {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "history_id", nullable = false)
    private Long historyId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private int rank;

    @Column(name = "rating_before", nullable = false)
    private int ratingBefore;

    @Column(name = "rating_after", nullable = false)
    private int ratingAfter;

    @Column(name = "rating_delta", nullable = false)
    private int ratingDelta;

    protected MatchHistoryScore() {}

    public MatchHistoryScore(Long historyId, UUID userId, int score, int rank, int ratingBefore, int ratingAfter) {
        this.historyId = historyId;
        this.userId = userId;
        this.score = score;
        this.rank = rank;
        this.ratingBefore = ratingBefore;
        this.ratingAfter = ratingAfter;
        this.ratingDelta = ratingAfter - ratingBefore;
    }

    public Long getId() { return id; }
    public Long getHistoryId() { return historyId; }
    public UUID getUserId() { return userId; }
    public int getScore() { return score; }
    public int getRank() { return rank; }
    public int getRatingBefore() { return ratingBefore; }
    public int getRatingAfter() { return ratingAfter; }
    public int getRatingDelta() { return ratingDelta; }
}
