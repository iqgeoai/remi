package com.remi.push;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    List<DeviceToken> findByUserId(UUID userId);
    Optional<DeviceToken> findByUserIdAndToken(UUID userId, String token);
}
