package com.remi.user.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {
  @Id private UUID id;

  @Column(nullable = false, unique = true) private String email;
  @Column(name = "email_normalized", nullable = false, unique = true) private String emailNormalized;
  @Column(nullable = false) private String username;
  @Column(name = "username_normalized", nullable = false, unique = true) private String usernameNormalized;
  @Column(name = "password_hash", nullable = false, length = 60) private String passwordHash;
  @Column(name = "email_verified", nullable = false) private boolean emailVerified;

  @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
  @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  protected UserEntity() {}
  public UserEntity(UUID id, String email, String emailNormalized, String username, String usernameNormalized, String passwordHash) {
    this.id = id;
    this.email = email;
    this.emailNormalized = emailNormalized;
    this.username = username;
    this.usernameNormalized = usernameNormalized;
    this.passwordHash = passwordHash;
    this.emailVerified = false;
  }

  public UUID getId() { return id; }
  public String getEmail() { return email; }
  public String getEmailNormalized() { return emailNormalized; }
  public String getUsername() { return username; }
  public String getUsernameNormalized() { return usernameNormalized; }
  public String getPasswordHash() { return passwordHash; }
  public boolean isEmailVerified() { return emailVerified; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  public void setPasswordHash(String h) { this.passwordHash = h; }
  public void markEmailVerified() { this.emailVerified = true; }
}
