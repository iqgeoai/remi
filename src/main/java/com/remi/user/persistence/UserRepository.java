package com.remi.user.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
  Optional<UserEntity> findByEmailNormalized(String emailNormalized);
  Optional<UserEntity> findByUsernameNormalized(String usernameNormalized);
  boolean existsByEmailNormalized(String emailNormalized);
  boolean existsByUsernameNormalized(String usernameNormalized);
}
