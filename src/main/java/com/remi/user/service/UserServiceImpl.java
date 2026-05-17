package com.remi.user.service;

import com.remi.auth.mail.MailService;
import com.remi.auth.password.EmailNormalizer;
import com.remi.auth.password.PasswordValidator;
import com.remi.auth.password.UsernameValidator;
import com.remi.user.domain.User;
import com.remi.user.persistence.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {
  private static final Duration VERIFICATION_TTL = Duration.ofHours(24);
  private static final Duration RESET_TTL = Duration.ofHours(1);

  private final UserRepository users;
  private final VerificationTokenRepository verifyTokens;
  private final PasswordResetTokenRepository resetTokens;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder encoder;
  private final MailService mail;
  private final Clock clock;

  public UserServiceImpl(UserRepository users, VerificationTokenRepository verifyTokens,
                         PasswordResetTokenRepository resetTokens, RefreshTokenRepository refreshTokens,
                         PasswordEncoder encoder, MailService mail, Clock clock) {
    this.users = users; this.verifyTokens = verifyTokens; this.resetTokens = resetTokens;
    this.refreshTokens = refreshTokens; this.encoder = encoder; this.mail = mail; this.clock = clock;
  }

  @Override
  @Transactional
  public User register(String email, String username, String rawPassword) {
    PasswordValidator.validate(rawPassword);
    UsernameValidator.validate(username);
    String emailNorm = EmailNormalizer.normalize(email);
    String usernameNorm = username.toLowerCase(Locale.ROOT);

    if (users.existsByEmailNormalized(emailNorm)) throw new EmailAlreadyTakenException(email);
    if (users.existsByUsernameNormalized(usernameNorm)) throw new UsernameAlreadyTakenException(username);

    UserEntity e = new UserEntity(UUID.randomUUID(), email, emailNorm, username, usernameNorm,
        encoder.encode(rawPassword));
    users.save(e);

    UUID tokenId = UUID.randomUUID();
    Instant expires = clock.instant().plus(VERIFICATION_TTL);
    verifyTokens.save(new VerificationTokenEntity(tokenId, e.getId(), expires));
    mail.sendVerification(e.getEmail(), e.getUsername(), tokenId);

    return toDomain(e);
  }

  @Override
  @Transactional
  public void verifyEmail(UUID token) {
    VerificationTokenEntity t = verifyTokens.findById(token)
        .orElseThrow(() -> new InvalidTokenException(InvalidTokenException.Kind.VERIFICATION, "not found"));
    if (t.getUsedAt() != null) throw new InvalidTokenException(InvalidTokenException.Kind.VERIFICATION, "already used");
    if (t.getExpiresAt().isBefore(clock.instant()))
      throw new InvalidTokenException(InvalidTokenException.Kind.VERIFICATION, "expired");
    t.markUsed(clock.instant());
    UserEntity u = users.findById(t.getUserId()).orElseThrow(() -> new UserNotFoundException(t.getUserId()));
    u.markEmailVerified();
  }

  @Override
  @Transactional
  public void requestPasswordReset(String email) {
    String emailNorm = EmailNormalizer.normalize(email);
    var user = users.findByEmailNormalized(emailNorm);
    if (user.isEmpty()) return;
    UserEntity u = user.get();
    UUID tokenId = UUID.randomUUID();
    Instant expires = clock.instant().plus(RESET_TTL);
    resetTokens.save(new PasswordResetTokenEntity(tokenId, u.getId(), expires));
    mail.sendPasswordReset(u.getEmail(), u.getUsername(), tokenId);
  }

  @Override
  @Transactional
  public void resetPassword(UUID token, String newRawPassword) {
    PasswordValidator.validate(newRawPassword);
    PasswordResetTokenEntity t = resetTokens.findById(token)
        .orElseThrow(() -> new InvalidTokenException(InvalidTokenException.Kind.PASSWORD_RESET, "not found"));
    if (t.getUsedAt() != null) throw new InvalidTokenException(InvalidTokenException.Kind.PASSWORD_RESET, "already used");
    if (t.getExpiresAt().isBefore(clock.instant()))
      throw new InvalidTokenException(InvalidTokenException.Kind.PASSWORD_RESET, "expired");
    UserEntity u = users.findById(t.getUserId()).orElseThrow(() -> new UserNotFoundException(t.getUserId()));
    u.setPasswordHash(encoder.encode(newRawPassword));
    t.markUsed(clock.instant());
    refreshTokens.revokeAllActiveForUser(u.getId(), clock.instant());
  }

  @Override
  @Transactional(readOnly = true)
  public User getById(UUID id) {
    return users.findById(id).map(this::toDomain).orElseThrow(() -> new UserNotFoundException(id));
  }

  @Override
  @Transactional(readOnly = true)
  public User getByEmail(String email) {
    String norm = EmailNormalizer.normalize(email);
    return users.findByEmailNormalized(norm).map(this::toDomain)
        .orElseThrow(() -> new UserNotFoundException(null));
  }

  private User toDomain(UserEntity e) {
    return new User(e.getId(), e.getEmail(), e.getUsername(), e.isEmailVerified(), e.getCreatedAt());
  }
}
