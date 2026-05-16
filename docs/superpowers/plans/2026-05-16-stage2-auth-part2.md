# Stage 2 — Auth + Users Implementation Plan (Part 2: Services → REST → E2E)

> **For agentic workers:** Continuation of `2026-05-16-stage2-auth-part1.md`. Same TDD discipline.

**Pre-requisite:** Part 1 (Phases A-E) completed — pom updates, V2 migration, config, domain records, validators, JwtService + filter, MailService, entities + repos.

---

## Phase F — Services (UserService + AuthService)

### Task F1: Custom exceptions (remaining)

**Files:**
- Create: `src/main/java/com/remi/user/service/EmailAlreadyTakenException.java`
- Create: `src/main/java/com/remi/user/service/UsernameAlreadyTakenException.java`
- Create: `src/main/java/com/remi/user/service/InvalidCredentialsException.java`
- Create: `src/main/java/com/remi/user/service/InvalidTokenException.java`
- Create: `src/main/java/com/remi/user/service/TokenReusedException.java`
- Create: `src/main/java/com/remi/user/service/UserNotFoundException.java`

Tasks B3+B4 already created `PasswordPolicyViolationException` and `UsernamePolicyViolationException`. This task adds the rest.

- [ ] **Step 1: Write all six**

```java
package com.remi.user.service;
public class EmailAlreadyTakenException extends RuntimeException {
  public EmailAlreadyTakenException(String email) { super("Email already taken: " + email); }
}
```
```java
package com.remi.user.service;
public class UsernameAlreadyTakenException extends RuntimeException {
  public UsernameAlreadyTakenException(String username) { super("Username already taken: " + username); }
}
```
```java
package com.remi.user.service;
public class InvalidCredentialsException extends RuntimeException {
  public InvalidCredentialsException() { super("Invalid credentials"); }
}
```
```java
package com.remi.user.service;
public class InvalidTokenException extends RuntimeException {
  public enum Kind { VERIFICATION, REFRESH, PASSWORD_RESET }
  private final Kind kind;
  public InvalidTokenException(Kind kind, String reason) { super(reason); this.kind = kind; }
  public Kind getKind() { return kind; }
}
```
```java
package com.remi.user.service;
public class TokenReusedException extends RuntimeException {
  public TokenReusedException() { super("Refresh token reuse detected — all sessions revoked"); }
}
```
```java
package com.remi.user.service;
import java.util.UUID;
public class UserNotFoundException extends RuntimeException {
  public UserNotFoundException(UUID id) { super("User not found: " + id); }
}
```

- [ ] **Step 2: Compile + commit**

```bash
mvn -q compile
git add src/main/java/com/remi/user/service/EmailAlreadyTakenException.java \
        src/main/java/com/remi/user/service/UsernameAlreadyTakenException.java \
        src/main/java/com/remi/user/service/InvalidCredentialsException.java \
        src/main/java/com/remi/user/service/InvalidTokenException.java \
        src/main/java/com/remi/user/service/TokenReusedException.java \
        src/main/java/com/remi/user/service/UserNotFoundException.java
git commit -m "feat(user): add remaining service exceptions (Email/Username taken, InvalidCreds, etc.)"
```

---

### Task F2: `UserService` interface + `UserServiceImpl` + IT

**Files:**
- Create: `src/main/java/com/remi/user/service/UserService.java`
- Create: `src/main/java/com/remi/user/service/UserServiceImpl.java`
- Create: `src/test/java/com/remi/user/api/MockMailServiceTestConfig.java` (used by all ITs in Stage 2)
- Create: `src/test/java/com/remi/user/service/UserServiceIT.java`

- [ ] **Step 1: Write `UserService` interface**

```java
package com.remi.user.service;

import com.remi.user.domain.User;
import java.util.UUID;

public interface UserService {
  User register(String email, String username, String rawPassword);
  void verifyEmail(UUID token);
  void requestPasswordReset(String email);
  void resetPassword(UUID token, String newRawPassword);
  User getById(UUID id);
  User getByEmail(String email);
}
```

- [ ] **Step 2: Write `MockMailServiceTestConfig.java`** (in test sources, importable by ITs)

Uses a static `SENT` list (deliberately — simpler than wiring a bean state holder; tests `clear()` it in `@BeforeEach`):

```java
package com.remi.user.api;

import com.remi.auth.mail.MailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@TestConfiguration
public class MockMailServiceTestConfig {
  public record SentMail(String kind, String toEmail, String username, UUID token) {}
  public static final List<SentMail> SENT = new CopyOnWriteArrayList<>();

  @Bean @Primary
  public MailService mockMailService() {
    return new MailService() {
      @Override public void sendVerification(String toEmail, String username, UUID token) {
        SENT.add(new SentMail("VERIFICATION", toEmail, username, token));
      }
      @Override public void sendPasswordReset(String toEmail, String username, UUID token) {
        SENT.add(new SentMail("PASSWORD_RESET", toEmail, username, token));
      }
    };
  }
}
```

- [ ] **Step 3: Write failing IT**

```java
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
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(MockMailServiceTestConfig.class)
class UserServiceIT {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired UserService userService;
  @Autowired UserRepository userRepo;
  @Autowired VerificationTokenRepository verifyRepo;
  @Autowired PasswordResetTokenRepository resetRepo;
  @Autowired RefreshTokenRepository refreshRepo;

  @BeforeEach void clearMail() { MockMailServiceTestConfig.SENT.clear(); }

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
    var u = userService.register("a@b.com", "user1", "passwordxx");
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
}
```

- [ ] **Step 4: Run (FAIL — UserServiceImpl missing)**

Run: `mvn test -Dtest=UserServiceIT`
Expected: compilation failure.

- [ ] **Step 5: Write `UserServiceImpl.java`**

```java
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
    if (user.isEmpty()) return;  // silent — no enumeration
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
```

- [ ] **Step 6: Run IT (PASS — 7 tests)** + **Commit**

```bash
mvn test -Dtest=UserServiceIT
git add src/main/java/com/remi/user/service/UserService.java \
        src/main/java/com/remi/user/service/UserServiceImpl.java \
        src/test/java/com/remi/user/api/MockMailServiceTestConfig.java \
        src/test/java/com/remi/user/service/UserServiceIT.java
git commit -m "feat(user): UserService — register, verifyEmail, requestPasswordReset, resetPassword + IT"
```

---

### Task F3: `AuthService` interface + `AuthServiceImpl` + IT

**Files:**
- Create: `src/main/java/com/remi/user/service/AuthService.java`
- Create: `src/main/java/com/remi/user/service/AuthServiceImpl.java`
- Create: `src/test/java/com/remi/user/service/AuthServiceIT.java`

- [ ] **Step 1: Write interface**

```java
package com.remi.user.service;

import com.remi.auth.domain.AuthTokens;
import java.util.UUID;

public interface AuthService {
  AuthTokens login(String emailOrUsername, String rawPassword);
  AuthTokens refresh(UUID refreshTokenId);
  void logout(UUID refreshTokenId);
  void logoutAll(UUID userId);
}
```

- [ ] **Step 2: Write failing IT**

```java
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
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(MockMailServiceTestConfig.class)
class AuthServiceIT {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired UserService userService;
  @Autowired AuthService authService;
  @Autowired RefreshTokenRepository refreshRepo;

  @BeforeEach void clear() { MockMailServiceTestConfig.SENT.clear(); }

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
    // do NOT verify
    assertThatThrownBy(() -> authService.login("a@b.com", "passwordxx"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void refreshRotatesTokens() {
    registerAndVerify("a@b.com", "user1");
    AuthTokens first = authService.login("a@b.com", "passwordxx");
    AuthTokens second = authService.refresh(UUID.fromString(first.refreshToken()));
    assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
    // Old refresh revoked + replaced_by populated
    var oldEntity = refreshRepo.findById(UUID.fromString(first.refreshToken())).orElseThrow();
    assertThat(oldEntity.getRevokedAt()).isNotNull();
    assertThat(oldEntity.getReplacedBy()).isEqualTo(UUID.fromString(second.refreshToken()));
  }

  @Test
  void refreshReuseDetectionRevokesAllSessions() {
    registerAndVerify("a@b.com", "user1");
    AuthTokens first = authService.login("a@b.com", "passwordxx");
    AuthTokens secondLogin = authService.login("a@b.com", "passwordxx");  // user logs in elsewhere
    authService.refresh(UUID.fromString(first.refreshToken()));  // rotation: first -> third
    // Now attacker tries to reuse first
    assertThatThrownBy(() -> authService.refresh(UUID.fromString(first.refreshToken())))
        .isInstanceOf(TokenReusedException.class);
    // After reuse detection, ALL active tokens for user are revoked, including the second login
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
}
```

- [ ] **Step 3: Run (FAIL — AuthServiceImpl missing)**

- [ ] **Step 4: Write `AuthServiceImpl.java`**

```java
package com.remi.user.service;

import com.remi.auth.domain.AuthTokens;
import com.remi.auth.jwt.JwtService;
import com.remi.auth.password.EmailNormalizer;
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
  @Transactional
  public AuthTokens refresh(UUID refreshTokenId) {
    RefreshTokenEntity rt = refreshTokens.findById(refreshTokenId)
        .orElseThrow(() -> new InvalidTokenException(InvalidTokenException.Kind.REFRESH, "not found"));
    if (rt.getRevokedAt() != null) {
      if (rt.getReplacedBy() != null) {
        // REUSE detected — revoke all active tokens for this user
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
    // accessExpiresAt is approximate — could be parsed back from JWT, but for the response it's fine
    Instant accessExp = clock.instant().plus(Duration.ofMinutes(15));
    return new AuthTokens(access, refreshId.toString(), accessExp);
  }
}
```

- [ ] **Step 5: Run IT (PASS — 8 tests)** + **Commit**

```bash
mvn test -Dtest=AuthServiceIT
git add src/main/java/com/remi/user/service/AuthService.java \
        src/main/java/com/remi/user/service/AuthServiceImpl.java \
        src/test/java/com/remi/user/service/AuthServiceIT.java
git commit -m "feat(user): AuthService — login, refresh with rotation+reuse detection, logout, logoutAll"
```

---

## Phase G — Security Config + Exception Handler Extensions

### Task G1: `SecurityConfig`

**Files:**
- Create: `src/main/java/com/remi/config/SecurityConfig.java`

- [ ] **Step 1: Write config**

```java
package com.remi.config;

import com.remi.auth.jwt.JsonAuthenticationEntryPoint;
import com.remi.auth.jwt.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                  JwtAuthFilter jwtAuthFilter,
                                                  JsonAuthenticationEntryPoint entryPoint) throws Exception {
    return http
        .csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a
            .requestMatchers("/api/auth/**").permitAll()
            .requestMatchers("/api/dev/**").permitAll()
            .anyRequest().authenticated())
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
        .build();
  }
}
```

- [ ] **Step 2: Compile + commit**

```bash
mvn -q compile
git add src/main/java/com/remi/config/SecurityConfig.java
git commit -m "feat(config): SecurityConfig — stateless, JWT filter, /api/auth/** + /api/dev/** public"
```

---

### Task G2: Extend `ApiExceptionHandler`

**Files:**
- Modify: `src/main/java/com/remi/api/ApiExceptionHandler.java`

- [ ] **Step 1: Add handlers**

Add the following methods inside the existing `ApiExceptionHandler` class:

```java
  @ExceptionHandler({com.remi.user.service.EmailAlreadyTakenException.class})
  public ResponseEntity<ApiError> emailTaken(com.remi.user.service.EmailAlreadyTakenException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
        .body(new ApiError("EMAIL_TAKEN", "Acest email este deja folosit."));
  }

  @ExceptionHandler({com.remi.user.service.UsernameAlreadyTakenException.class})
  public ResponseEntity<ApiError> usernameTaken(com.remi.user.service.UsernameAlreadyTakenException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
        .body(new ApiError("USERNAME_TAKEN", "Acest username este deja folosit."));
  }

  @ExceptionHandler(com.remi.user.service.InvalidCredentialsException.class)
  public ResponseEntity<ApiError> invalidCreds(com.remi.user.service.InvalidCredentialsException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST)
        .body(new ApiError("INVALID_CREDENTIALS", "Credențiale invalide sau email neverificat."));
  }

  @ExceptionHandler(com.remi.user.service.InvalidTokenException.class)
  public ResponseEntity<ApiError> invalidToken(com.remi.user.service.InvalidTokenException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST)
        .body(new ApiError("INVALID_TOKEN", "Token invalid sau expirat."));
  }

  @ExceptionHandler(com.remi.user.service.TokenReusedException.class)
  public ResponseEntity<ApiError> tokenReused(com.remi.user.service.TokenReusedException e) {
    log.warn("Refresh token reuse detected — all user sessions revoked");
    return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
        .body(new ApiError("TOKEN_REUSED", "Sesiunea a fost compromisă, re-autentificare necesară."));
  }

  @ExceptionHandler(com.remi.user.service.UserNotFoundException.class)
  public ResponseEntity<ApiError> userNotFound(com.remi.user.service.UserNotFoundException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
        .body(new ApiError("USER_NOT_FOUND", "Utilizator inexistent."));
  }

  @ExceptionHandler(com.remi.user.service.PasswordPolicyViolationException.class)
  public ResponseEntity<ApiError> passwordPolicy(com.remi.user.service.PasswordPolicyViolationException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST)
        .body(new ApiError("PASSWORD_POLICY", e.getMessage()));
  }

  @ExceptionHandler(com.remi.user.service.UsernamePolicyViolationException.class)
  public ResponseEntity<ApiError> usernamePolicy(com.remi.user.service.UsernamePolicyViolationException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST)
        .body(new ApiError("USERNAME_POLICY", e.getMessage()));
  }
```

- [ ] **Step 2: Compile + commit**

```bash
mvn -q compile
git add src/main/java/com/remi/api/ApiExceptionHandler.java
git commit -m "feat(api): extend ApiExceptionHandler for Stage 2 auth exceptions"
```

---

## Phase H — REST Controllers

### Task H1: Auth + User request/response DTOs

**Files:**
- Create: `src/main/java/com/remi/user/api/RegisterRequest.java`
- Create: `src/main/java/com/remi/user/api/LoginRequest.java`
- Create: `src/main/java/com/remi/user/api/RefreshRequest.java`
- Create: `src/main/java/com/remi/user/api/LogoutRequest.java`
- Create: `src/main/java/com/remi/user/api/VerifyEmailRequest.java`
- Create: `src/main/java/com/remi/user/api/RequestPasswordResetRequest.java`
- Create: `src/main/java/com/remi/user/api/ResetPasswordRequest.java`
- Create: `src/main/java/com/remi/user/api/AuthTokensResponse.java`
- Create: `src/main/java/com/remi/user/api/UserResponse.java`

- [ ] **Step 1: Write all DTOs**

```java
package com.remi.user.api;
import jakarta.validation.constraints.*;
public record RegisterRequest(
    @NotBlank @Email @Size(max=254) String email,
    @NotBlank @Size(min=3, max=20) @Pattern(regexp="^[a-zA-Z0-9_-]+$") String username,
    @NotBlank @Size(min=10, max=200) String password
) {}
```
```java
package com.remi.user.api;
import jakarta.validation.constraints.NotBlank;
public record LoginRequest(@NotBlank String emailOrUsername, @NotBlank String password) {}
```
```java
package com.remi.user.api;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record RefreshRequest(@NotNull UUID refreshToken) {}
```
```java
package com.remi.user.api;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record LogoutRequest(@NotNull UUID refreshToken) {}
```
```java
package com.remi.user.api;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record VerifyEmailRequest(@NotNull UUID token) {}
```
```java
package com.remi.user.api;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
public record RequestPasswordResetRequest(@NotBlank @Email String email) {}
```
```java
package com.remi.user.api;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record ResetPasswordRequest(@NotNull UUID token, @NotBlank @Size(min=10, max=200) String newPassword) {}
```
```java
package com.remi.user.api;
import com.remi.auth.domain.AuthTokens;
import java.time.Instant;
public record AuthTokensResponse(String accessToken, String refreshToken, Instant accessExpiresAt) {
  public static AuthTokensResponse of(AuthTokens t) {
    return new AuthTokensResponse(t.accessToken(), t.refreshToken(), t.accessExpiresAt());
  }
}
```
```java
package com.remi.user.api;
import com.remi.user.domain.User;
import java.time.Instant;
import java.util.UUID;
public record UserResponse(UUID id, String email, String username, boolean emailVerified, Instant createdAt) {
  public static UserResponse of(User u) {
    return new UserResponse(u.id(), u.email(), u.username(), u.emailVerified(), u.createdAt());
  }
}
```

- [ ] **Step 2: Compile + commit**

```bash
mvn -q compile
git add src/main/java/com/remi/user/api/
git commit -m "feat(user): request/response DTOs for auth + user endpoints"
```

---

### Task H2: `AuthController` + `UserController`

**Files:**
- Create: `src/main/java/com/remi/user/api/AuthController.java`
- Create: `src/main/java/com/remi/user/api/UserController.java`

- [ ] **Step 1: Write `AuthController.java`**

```java
package com.remi.user.api;

import com.remi.user.service.AuthService;
import com.remi.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final UserService userService;
  private final AuthService authService;

  public AuthController(UserService userService, AuthService authService) {
    this.userService = userService;
    this.authService = authService;
  }

  @PostMapping("/register")
  public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req) {
    var user = userService.register(req.email(), req.username(), req.password());
    return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.of(user));
  }

  @PostMapping("/verify-email")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
    userService.verifyEmail(req.token());
  }

  @PostMapping("/login")
  public AuthTokensResponse login(@Valid @RequestBody LoginRequest req) {
    return AuthTokensResponse.of(authService.login(req.emailOrUsername(), req.password()));
  }

  @PostMapping("/refresh")
  public AuthTokensResponse refresh(@Valid @RequestBody RefreshRequest req) {
    return AuthTokensResponse.of(authService.refresh(req.refreshToken()));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@Valid @RequestBody LogoutRequest req) {
    authService.logout(req.refreshToken());
  }

  @PostMapping("/request-password-reset")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void requestPasswordReset(@Valid @RequestBody RequestPasswordResetRequest req) {
    userService.requestPasswordReset(req.email());
  }

  @PostMapping("/reset-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
    userService.resetPassword(req.token(), req.newPassword());
  }
}
```

- [ ] **Step 2: Write `UserController.java`**

```java
package com.remi.user.api;

import com.remi.user.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {
  private final UserService userService;
  public UserController(UserService userService) { this.userService = userService; }

  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal UUID userId) {
    return UserResponse.of(userService.getById(userId));
  }
}
```

- [ ] **Step 3: Compile + commit**

```bash
mvn -q compile
git add src/main/java/com/remi/user/api/AuthController.java \
        src/main/java/com/remi/user/api/UserController.java
git commit -m "feat(user): AuthController (/api/auth/*) + UserController (/api/users/me)"
```

---

## Phase I — E2E + final

### Task I1: `AuthApiE2ETest` — happy path + error paths

**Files:**
- Create: `src/test/java/com/remi/user/api/AuthApiE2ETest.java`

- [ ] **Step 1: Write E2E test**

```java
package com.remi.user.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(MockMailServiceTestConfig.class)
class AuthApiE2ETest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper om;

  @BeforeEach void clear() { MockMailServiceTestConfig.SENT.clear(); }

  @Test
  void fullHappyPath_register_verify_login_me_refresh_logout() throws Exception {
    // 1. Register
    String regBody = """
        {"email":"alice@example.com","username":"alice","password":"passwordxx"}""";
    mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(regBody))
        .andExpect(status().isCreated());

    // 2. Verify email
    var verificationToken = MockMailServiceTestConfig.SENT.get(0).token();
    String verifyBody = String.format("{\"token\":\"%s\"}", verificationToken);
    mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content(verifyBody))
        .andExpect(status().isNoContent());

    // 3. Login
    String loginBody = """
        {"emailOrUsername":"alice","password":"passwordxx"}""";
    String loginResp = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    JsonNode loginJson = om.readTree(loginResp);
    String accessToken = loginJson.get("accessToken").asText();
    String refreshToken = loginJson.get("refreshToken").asText();

    // 4. GET /me with bearer
    mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alice"));

    // 5. Refresh
    String refreshBody = String.format("{\"refreshToken\":\"%s\"}", refreshToken);
    String refreshResp = mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    String newRefresh = om.readTree(refreshResp).get("refreshToken").asText();

    // 6. Logout the new refresh
    String logoutBody = String.format("{\"refreshToken\":\"%s\"}", newRefresh);
    mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content(logoutBody))
        .andExpect(status().isNoContent());
  }

  @Test
  void registerWithBadEmailReturns400() throws Exception {
    String body = """
        {"email":"not-an-email","username":"user1","password":"passwordxx"}""";
    mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void registerWithShortPasswordReturns400() throws Exception {
    String body = """
        {"email":"a@b.com","username":"user1","password":"short"}""";
    mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void loginUnverifiedUserReturnsSameErrorAsWrongPassword() throws Exception {
    // Register but do not verify
    String regBody = """
        {"email":"bob@example.com","username":"bob","password":"passwordxx"}""";
    mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(regBody))
        .andExpect(status().isCreated());

    String loginBody = """
        {"emailOrUsername":"bob","password":"passwordxx"}""";
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void meWithoutTokenReturns401() throws Exception {
    mvc.perform(get("/api/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void meWithMalformedTokenReturns401() throws Exception {
    mvc.perform(get("/api/users/me").header("Authorization", "Bearer not.a.real.token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void devGamesEndpointStaysOpenWithoutAuth() throws Exception {
    String body = """
        {"numPlayers":2,"mode":"ETALAT","difficulty":"MED","seed":42}""";
    mvc.perform(post("/api/dev/games").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());
  }
}
```

- [ ] **Step 2: Run (PASS — requires Docker)**

Run: `mvn test -Dtest=AuthApiE2ETest`
Expected: 7/7 pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/remi/user/api/AuthApiE2ETest.java
git commit -m "test(user): E2E — full auth flow + error paths + dev endpoint stays open"
```

---

### Task I2: Update JaCoCo coverage gate for new packages

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add new rules to the JaCoCo `check` execution**

In `pom.xml`, find the existing `<rule>` block under jacoco-maven-plugin's `check` execution. Add two more rules:

```xml
                <rule>
                  <element>PACKAGE</element>
                  <includes>
                    <include>com.remi.auth.jwt</include>
                    <include>com.remi.auth.password</include>
                  </includes>
                  <limits><limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.85</minimum></limit></limits>
                </rule>
                <rule>
                  <element>PACKAGE</element>
                  <includes><include>com.remi.user.service</include></includes>
                  <limits><limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.85</minimum></limit></limits>
                </rule>
```

- [ ] **Step 2: Verify gate**

Run: `mvn verify`
Expected: BUILD SUCCESS (or BUILD FAILURE if some new package is below 85% — in which case add targeted tests and re-run).

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: extend JaCoCo gate — 85% on com.remi.auth.{jwt,password}, com.remi.user.service"
```

---

### Task I3: Update `README.md` with auth quickstart

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add an "Auth (Stage 2)" section** after the existing "Try the API" section:

```markdown
## Auth (Stage 2)

Stage 2 adds user accounts. Required env vars for prod (SMTP):
```bash
export JWT_SECRET="your-256-bit-secret-rotate-on-compromise"
export SMTP_HOST=smtp.mailtrap.io
export SMTP_PORT=587
export SMTP_USER=...
export SMTP_PASS=...
export MAIL_FROM=noreply@remi.example
export MAIL_VERIFICATION_LINK_BASE=https://app.remi.example/verify
export MAIL_RESET_LINK_BASE=https://app.remi.example/reset
```

### Try the auth flow

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","username":"alice","password":"passwordxx"}'

# (Check email for verification token, then:)
curl -X POST http://localhost:8080/api/auth/verify-email \
  -H 'Content-Type: application/json' \
  -d '{"token":"<token-from-email>"}'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"emailOrUsername":"alice","password":"passwordxx"}'
# → {"accessToken":"...","refreshToken":"...","accessExpiresAt":"..."}

# Authenticated request
curl http://localhost:8080/api/users/me -H "Authorization: Bearer <accessToken>"

# Refresh
curl -X POST http://localhost:8080/api/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<refreshToken>"}'

# Logout
curl -X POST http://localhost:8080/api/auth/logout \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<refreshToken>"}'
```

The Stage 1 `/api/dev/games/*` endpoints remain accessible without auth (whitelist in `SecurityConfig`). They will be replaced by authenticated `/api/games/*` in Stage 3.
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: README — add Stage 2 auth quickstart + env vars"
```

---

## Stage 2 complete

All phases (A-I) done. Engine unchanged, `com.remi.engine.*` still Spring-free. New packages `com.remi.auth.*` and `com.remi.user.*` provide register + verify + login + refresh + logout + password reset. Coverage gate extended for new packages at 85%.

**Next stage** (3 — multiplayer + lobby) gets its own spec via `superpowers:brainstorming`. It will introduce `owner_id` / `players[].user_id` linkage to the `games` table and replace `/api/dev/*` with authenticated `/api/games/*` plus WebSocket.
