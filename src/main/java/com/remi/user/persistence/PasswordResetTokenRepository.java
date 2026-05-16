package com.remi.user.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {}
