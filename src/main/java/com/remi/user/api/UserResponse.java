package com.remi.user.api;
import com.remi.user.domain.User;
import java.time.Instant;
import java.util.UUID;
public record UserResponse(UUID id, String email, String username, boolean emailVerified, Instant createdAt) {
  public static UserResponse of(User u) {
    return new UserResponse(u.id(), u.email(), u.username(), u.emailVerified(), u.createdAt());
  }
}
