package com.remi.user.service;

import com.remi.auth.domain.AuthTokens;
import com.remi.auth.jwt.JwtService;
import com.remi.user.domain.User;
import com.remi.user.persistence.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {
  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder encoder;
  private final JwtService jwt;
  private final Clock clock;
  private final Duration refreshTtl;

  public AuthServiceImpl(UserRepository users, RefreshTokenRepository refreshTokens,
                         PasswordEncoder encoder, JwtService jwt, Clock clock,
                         @Value("${jwt.refresh-ttl}") Duration refreshTtl) {
    this.users = users; this.refreshTokens = refreshTokens; this.encoder = encoder;
    this.jwt = jwt; this.clock = clock; this.refreshTtl = refreshTtl;
  }

  @Override
  @Transactional
  public AuthTokens login(String emailOrUsername, String rawPassword) {
    String norm = (emailOrUsername == null) ? "" : emailOrUsername.trim().toLowerCase(Locale.ROOT);
    UserEntity u = users.findByEmailNormalized(norm)
        .or(() -> users.findByUsernameNormalized(norm))
        .orElseThrow(InvalidCredentialsException::new);
    if (!encoder.matches(rawPassword, u.getPasswordHash())) throw new InvalidCredentialsException();
    if (!u.isEmailVerified()) throw new InvalidCredentialsException();
    return issueTokensFor(u);
  }

  @Override
  @Transactional(noRollbackFor = TokenReusedException.class)
  public AuthTokens refresh(UUID refreshTokenId) {
    RefreshTokenEntity rt = refreshTokens.findById(refreshTokenId)
        .orElseThrow(() -> new InvalidTokenException(InvalidTokenException.Kind.REFRESH, "not found"));
    if (rt.getRevokedAt() != null) {
      if (rt.getReplacedBy() != null) {
        refreshTokens.revokeAllActiveForUser(rt.getUserId(), clock.instant());
        throw new TokenReusedException();
      }
      throw new InvalidTokenException(InvalidTokenException.Kind.REFRESH, "revoked");
    }
    if (rt.getExpiresAt().isBefore(clock.instant()))
      throw new InvalidTokenException(InvalidTokenException.Kind.REFRESH, "expired");

    UserEntity u = users.findById(rt.getUserId()).orElseThrow(() -> new UserNotFoundException(rt.getUserId()));
    AuthTokens newPair = issueTokensFor(u);
    rt.rotate(clock.instant(), UUID.fromString(newPair.refreshToken()));
    return newPair;
  }

  @Override
  @Transactional
  public void logout(UUID refreshTokenId) {
    refreshTokens.findById(refreshTokenId).ifPresent(t -> {
      if (t.getRevokedAt() == null) t.revoke(clock.instant());
    });
  }

  @Override
  @Transactional
  public void logoutAll(UUID userId) {
    refreshTokens.revokeAllActiveForUser(userId, clock.instant());
  }

  private AuthTokens issueTokensFor(UserEntity u) {
    User domain = new User(u.getId(), u.getEmail(), u.getUsername(), u.isEmailVerified(), u.getCreatedAt());
    String access = jwt.issueAccessToken(domain);
    UUID refreshId = UUID.randomUUID();
    Instant expires = clock.instant().plus(refreshTtl);
    refreshTokens.save(new RefreshTokenEntity(refreshId, u.getId(), expires));
    Instant accessExp = clock.instant().plus(Duration.ofMinutes(15));
    return new AuthTokens(access, refreshId.toString(), accessExp);
  }
}
