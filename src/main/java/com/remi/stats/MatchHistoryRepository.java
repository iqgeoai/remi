package com.remi.stats;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface MatchHistoryRepository extends JpaRepository<MatchHistory, Long> {
    Optional<MatchHistory> findByGameId(UUID gameId);
}
