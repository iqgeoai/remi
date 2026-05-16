package com.remi.user.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
  @Modifying
  @Query("UPDATE RefreshTokenEntity r SET r.revokedAt = :when WHERE r.userId = :userId AND r.revokedAt IS NULL")
  int revokeAllActiveForUser(@Param("userId") UUID userId, @Param("when") Instant when);
}
