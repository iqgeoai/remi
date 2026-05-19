package com.remi.user.persistence;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
  Optional<UserEntity> findByEmailNormalized(String emailNormalized);
  Optional<UserEntity> findByUsernameNormalized(String usernameNormalized);
  boolean existsByEmailNormalized(String emailNormalized);
  boolean existsByUsernameNormalized(String usernameNormalized);
  List<UserEntity> findTop20ByUsernameNormalizedStartingWith(String prefix);
  List<UserEntity> findTop50ByOrderByRatingDesc(PageRequest page);
}
