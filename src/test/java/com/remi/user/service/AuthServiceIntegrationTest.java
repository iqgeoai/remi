package com.remi.user.service;

import com.remi.auth.domain.AuthTokens;
import com.remi.user.api.MockMailServiceTestConfig;
import com.remi.user.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(MockMailServiceTestConfig.class)
class AuthServiceIntegrationTest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired UserService userService;
  @Autowired AuthService authService;
  @Autowired RefreshTokenRepository refreshRepo;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach void resetState() {
    MockMailServiceTestConfig.SENT.clear();
    jdbc.execute("TRUNCATE refresh_tokens, verification_tokens, password_reset_tokens, users CASCADE");
  }

  private UUID registerAndVerify(String email, String username) {
    var u = userService.register(email, username, "passwordxx");
    userService.verifyEmail(MockMailServiceTestConfig.SENT.get(0).token());
    return u.id();
  }

  @Test
  void loginSucceedsWithEmailAndCorrectPassword() {
    registerAndVerify("a@b.com", "user1");
    AuthTokens tokens = authService.login("a@b.com", "passwordxx");
    assertThat(tokens.accessToken()).isNotBlank();
    assertThat(tokens.refreshToken()).isNotBlank();
  }

  @Test
  void loginSucceedsWithUsernameAndCorrectPassword() {
    registerAndVerify("a@b.com", "user1");
    AuthTokens tokens = authService.login("user1", "passwordxx");
    assertThat(tokens.accessToken()).isNotBlank();
  }

  @Test
  void loginRejectsWrongPassword() {
    registerAndVerify("a@b.com", "user1");
    assertThatThrownBy(() -> authService.login("a@b.com", "wrongpassword"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void loginRejectsUnknownUserWithSameExceptionType() {
    assertThatThrownBy(() -> authService.login("nobody@nowhere.com", "passwordxx"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void loginRejectsUnverifiedUserWithSameExceptionType() {
    userService.register("a@b.com", "user1", "passwordxx");
    assertThatThrownBy(() -> authService.login("a@b.com", "passwordxx"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void refreshRotatesTokens() {
    registerAndVerify("a@b.com", "user1");
    AuthTokens first = authService.login("a@b.com", "passwordxx");
    AuthTokens second = authService.refresh(UUID.fromString(first.refreshToken()));
    assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
    var oldEntity = refreshRepo.findById(UUID.fromString(first.refreshToken())).orElseThrow();
    assertThat(oldEntity.getRevokedAt()).isNotNull();
    assertThat(oldEntity.getReplacedBy()).isEqualTo(UUID.fromString(second.refreshToken()));
  }

  @Test
  void refreshReuseDetectionRevokesAllSessions() {
    registerAndVerify("a@b.com", "user1");
    AuthTokens first = authService.login("a@b.com", "passwordxx");
    AuthTokens secondLogin = authService.login("a@b.com", "passwordxx");
    authService.refresh(UUID.fromString(first.refreshToken()));
    assertThatThrownBy(() -> authService.refresh(UUID.fromString(first.refreshToken())))
        .isInstanceOf(TokenReusedException.class);
    var secondLoginEntity = refreshRepo.findById(UUID.fromString(secondLogin.refreshToken())).orElseThrow();
    assertThat(secondLoginEntity.getRevokedAt()).isNotNull();
  }

  @Test
  void logoutRevokesOnlyTheGivenToken() {
    registerAndVerify("a@b.com", "user1");
    AuthTokens t1 = authService.login("a@b.com", "passwordxx");
    AuthTokens t2 = authService.login("a@b.com", "passwordxx");
    authService.logout(UUID.fromString(t1.refreshToken()));
    assertThat(refreshRepo.findById(UUID.fromString(t1.refreshToken())).orElseThrow().getRevokedAt()).isNotNull();
    assertThat(refreshRepo.findById(UUID.fromString(t2.refreshToken())).orElseThrow().getRevokedAt()).isNull();
  }

  @Test
  void logoutIsNoopForUnknownToken() {
    assertThatNoException().isThrownBy(() -> authService.logout(UUID.randomUUID()));
  }

  @Test
  void logoutIsNoopForAlreadyRevokedToken() {
    registerAndVerify("a@b.com", "user1");
    AuthTokens t1 = authService.login("a@b.com", "passwordxx");
    UUID rt = UUID.fromString(t1.refreshToken());
    authService.logout(rt);
    Instant first = refreshRepo.findById(rt).orElseThrow().getRevokedAt();
    authService.logout(rt);
    Instant second = refreshRepo.findById(rt).orElseThrow().getRevokedAt();
    assertThat(second).isEqualTo(first);
  }

  @Test
  void refreshRejectsUnknownToken() {
    assertThatThrownBy(() -> authService.refresh(UUID.randomUUID()))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void refreshRejectsRevokedTokenWithoutReplacement() {
    UUID userId = registerAndVerify("a@b.com", "user1");
    UUID rtId = UUID.randomUUID();
    RefreshTokenEntity rt = new RefreshTokenEntity(rtId, userId, Instant.now().plusSeconds(3600));
    rt.revoke(Instant.now());
    refreshRepo.save(rt);
    assertThatThrownBy(() -> authService.refresh(rtId))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void refreshRejectsExpiredToken() {
    UUID userId = registerAndVerify("a@b.com", "user1");
    UUID rtId = UUID.randomUUID();
    refreshRepo.save(new RefreshTokenEntity(rtId, userId, Instant.now().minusSeconds(60)));
    assertThatThrownBy(() -> authService.refresh(rtId))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void logoutAllRevokesEveryActiveSession() {
    UUID userId = registerAndVerify("a@b.com", "user1");
    AuthTokens t1 = authService.login("a@b.com", "passwordxx");
    AuthTokens t2 = authService.login("a@b.com", "passwordxx");
    authService.logoutAll(userId);
    assertThat(refreshRepo.findById(UUID.fromString(t1.refreshToken())).orElseThrow().getRevokedAt()).isNotNull();
    assertThat(refreshRepo.findById(UUID.fromString(t2.refreshToken())).orElseThrow().getRevokedAt()).isNotNull();
  }
}
