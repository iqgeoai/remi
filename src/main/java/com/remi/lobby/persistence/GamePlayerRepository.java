package com.remi.lobby.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GamePlayerRepository extends JpaRepository<GamePlayerEntity, GamePlayerEntity.PK> {
  List<GamePlayerEntity> findByGameIdOrderByPlayerIdxAsc(UUID gameId);
  List<GamePlayerEntity> findByUserId(UUID userId);

  @Query("SELECT COUNT(p) FROM GamePlayerEntity p WHERE p.gameId = :gameId")
  long countByGameId(@Param("gameId") UUID gameId);

  @Query("SELECT p.playerIdx FROM GamePlayerEntity p WHERE p.gameId = :gameId AND p.userId = :userId")
  Optional<Integer> findSeat(@Param("gameId") UUID gameId, @Param("userId") UUID userId);

  boolean existsByGameIdAndUserId(UUID gameId, UUID userId);
}
