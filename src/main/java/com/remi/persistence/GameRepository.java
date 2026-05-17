package com.remi.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface GameRepository extends JpaRepository<GameEntity, UUID> {
  java.util.Optional<GameEntity> findByJoinCode(String joinCode);
  java.util.List<GameEntity> findByVisibility(com.remi.lobby.domain.GameVisibility visibility);
}
