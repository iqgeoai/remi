package com.remi.stats;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface MatchHistoryScoreRepository extends JpaRepository<MatchHistoryScore, Long> {
    List<MatchHistoryScore> findByUserIdOrderByIdDesc(UUID userId, PageRequest page);

    long countByUserId(UUID userId);

    @Query("SELECT COUNT(s) FROM MatchHistoryScore s WHERE s.userId = :uid AND s.rank = 1")
    long countWins(@Param("uid") UUID userId);

    @Query("SELECT COALESCE(SUM(s.score), 0) FROM MatchHistoryScore s WHERE s.userId = :uid")
    long totalPoints(@Param("uid") UUID userId);
}
