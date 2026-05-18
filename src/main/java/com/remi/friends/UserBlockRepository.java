package com.remi.friends;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {
    Optional<UserBlock> findByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
    List<UserBlock> findByBlockerId(UUID blockerId);
    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
}
