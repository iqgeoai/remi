package com.remi.user.service;

import com.remi.user.api.MockMailServiceTestConfig;
import com.remi.user.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
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
class UserServiceIntegrationTest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired UserService userService;
  @Autowired UserRepository userRepo;
  @Autowired VerificationTokenRepository verifyRepo;
  @Autowired PasswordResetTokenRepository resetRepo;
  @Autowired RefreshTokenRepository refreshRepo;

  @BeforeEach void resetState() {
    MockMailServiceTestConfig.SENT.clear();
    refreshRepo.deleteAll();
    verifyRepo.deleteAll();
    resetRepo.deleteAll();
    userRepo.deleteAll();
  }

  @Test
  void registerCreatesUserAndDispatchesVerificationEmail() {
    var u = userService.register("Foo@Bar.com", "user1", "passwordxx");
    assertThat(u.email()).isEqualTo("Foo@Bar.com");
    assertThat(u.emailVerified()).isFalse();
    assertThat(MockMailServiceTestConfig.SENT).hasSize(1);
    assertThat(MockMailServiceTestConfig.SENT.get(0).kind()).isEqualTo("VERIFICATION");
  }

  @Test
  void registerRejectsDuplicateEmailCaseInsensitive() {
    userService.register("a@b.com", "user1", "passwordxx");
    assertThatThrownBy(() -> userService.register("A@B.COM", "user2", "passwordxx"))
        .isInstanceOf(EmailAlreadyTakenException.class);
  }

  @Test
  void registerRejectsDuplicateUsernameCaseInsensitive() {
    userService.register("a@b.com", "User1", "passwordxx");
    assertThatThrownBy(() -> userService.register("c@d.com", "user1", "passwordxx"))
        .isInstanceOf(UsernameAlreadyTakenException.class);
  }

  @Test
  void verifyEmailMarksUserVerified() {
    var u = userService.register("a@b.com", "user1", "passwordxx");
    var token = MockMailServiceTestConfig.SENT.get(0).token();
    userService.verifyEmail(token);
    var reloaded = userRepo.findById(u.id()).orElseThrow();
    assertThat(reloaded.isEmailVerified()).isTrue();
  }

  @Test
  void verifyEmailRejectsAlreadyUsedToken() {
    userService.register("a@b.com", "user1", "passwordxx");
    var token = MockMailServiceTestConfig.SENT.get(0).token();
    userService.verifyEmail(token);
    assertThatThrownBy(() -> userService.verifyEmail(token))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void requestPasswordResetSilentForUnknownEmail() {
    assertThatNoException().isThrownBy(() -> userService.requestPasswordReset("unknown@nowhere.com"));
    assertThat(MockMailServiceTestConfig.SENT).isEmpty();
  }

  @Test
  void requestPasswordResetSendsForKnownEmail() {
    userService.register("a@b.com", "user1", "passwordxx");
    MockMailServiceTestConfig.SENT.clear();
    userService.requestPasswordReset("a@b.com");
    assertThat(MockMailServiceTestConfig.SENT).hasSize(1);
    assertThat(MockMailServiceTestConfig.SENT.get(0).kind()).isEqualTo("PASSWORD_RESET");
  }

  @Test
  void verifyEmailRejectsUnknownToken() {
    assertThatThrownBy(() -> userService.verifyEmail(UUID.randomUUID()))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void verifyEmailRejectsExpiredToken() {
    var u = userService.register("a@b.com", "user1", "passwordxx");
    // Insert an expired verification token bypassing the service so we hit the expired branch.
    UUID expiredTokenId = UUID.randomUUID();
    verifyRepo.save(new VerificationTokenEntity(expiredTokenId, u.id(), Instant.now().minusSeconds(60)));
    assertThatThrownBy(() -> userService.verifyEmail(expiredTokenId))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void resetPasswordHappyPathChangesHashAndRevokesActiveRefreshTokens() {
    var u = userService.register("a@b.com", "user1", "passwordxx");
    String origHash = userRepo.findById(u.id()).orElseThrow().getPasswordHash();
    // active refresh token to prove revoke
    UUID activeRefresh = UUID.randomUUID();
    refreshRepo.save(new RefreshTokenEntity(activeRefresh, u.id(), Instant.now().plusSeconds(3600)));

    userService.requestPasswordReset("a@b.com");
    var resetToken = MockMailServiceTestConfig.SENT.get(MockMailServiceTestConfig.SENT.size() - 1).token();
    userService.resetPassword(resetToken, "newpasswordxx");

    String newHash = userRepo.findById(u.id()).orElseThrow().getPasswordHash();
    assertThat(newHash).isNotEqualTo(origHash);
    assertThat(refreshRepo.findById(activeRefresh).orElseThrow().getRevokedAt()).isNotNull();
  }

  @Test
  void resetPasswordRejectsUnknownToken() {
    assertThatThrownBy(() -> userService.resetPassword(UUID.randomUUID(), "newpasswordxx"))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void resetPasswordRejectsAlreadyUsedToken() {
    var u = userService.register("a@b.com", "user1", "passwordxx");
    userService.requestPasswordReset("a@b.com");
    var resetToken = MockMailServiceTestConfig.SENT.get(MockMailServiceTestConfig.SENT.size() - 1).token();
    userService.resetPassword(resetToken, "newpasswordxx");
    assertThatThrownBy(() -> userService.resetPassword(resetToken, "anotherpasswordxx"))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void resetPasswordRejectsExpiredToken() {
    var u = userService.register("a@b.com", "user1", "passwordxx");
    UUID expired = UUID.randomUUID();
    resetRepo.save(new PasswordResetTokenEntity(expired, u.id(), Instant.now().minusSeconds(60)));
    assertThatThrownBy(() -> userService.resetPassword(expired, "newpasswordxx"))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void resetPasswordValidatesNewPasswordPolicyBeforeTokenLookup() {
    assertThatThrownBy(() -> userService.resetPassword(UUID.randomUUID(), "short"))
        .isInstanceOf(PasswordPolicyViolationException.class);
  }

  @Test
  void getByIdReturnsUser() {
    var created = userService.register("a@b.com", "user1", "passwordxx");
    var fetched = userService.getById(created.id());
    assertThat(fetched.id()).isEqualTo(created.id());
    assertThat(fetched.username()).isEqualTo("user1");
  }

  @Test
  void getByIdThrowsWhenMissing() {
    assertThatThrownBy(() -> userService.getById(UUID.randomUUID()))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void getByEmailReturnsUserCaseInsensitive() {
    userService.register("Foo@Bar.com", "user1", "passwordxx");
    var fetched = userService.getByEmail("FOO@bar.COM");
    assertThat(fetched.email()).isEqualTo("Foo@Bar.com");
  }

  @Test
  void getByEmailThrowsWhenMissing() {
    assertThatThrownBy(() -> userService.getByEmail("nobody@nowhere.com"))
        .isInstanceOf(UserNotFoundException.class);
  }
}
