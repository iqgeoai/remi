# Stage 2 — Auth + Users Implementation Plan (Part 1: Setup → Persistence)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add user accounts with register + email verification + login (JWT access+refresh) + password reset, on top of Stage 1's engine + REST.

**Architecture:** Spring Security framework with a custom `OncePerRequestFilter` validating HS256 JWT access tokens. Refresh tokens as opaque UUIDs in DB with one-time-use rotation + reuse detection. SMTP via Spring Mail (mock bean in tests). New packages `com.remi.auth` and `com.remi.user`; `com.remi.engine` stays Spring-free. Spec: `docs/superpowers/specs/2026-05-16-stage2-auth-design.md`.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring Security, jjwt 0.12.x, spring-boot-starter-mail, Maven, PostgreSQL 16, Flyway, Jackson, JUnit 5, AssertJ, Testcontainers.

---

## File Structure

```
pom.xml                                             (modify: add deps)
src/main/resources/
  application.yml                                   (modify: add jwt+mail keys)
  db/migration/V2__init_users.sql                   (create)
src/test/resources/
  application-test.yml                              (modify: add jwt+mail test keys)
src/main/java/com/remi/
  auth/
    domain/
      AuthTokens.java
      JwtClaims.java
    password/
      PasswordValidator.java
      UsernameValidator.java
      EmailNormalizer.java
    jwt/
      JwtService.java
      JwtServiceImpl.java
      JwtAuthFilter.java
      JsonAuthenticationEntryPoint.java
    mail/
      MailService.java
      SmtpMailService.java
  user/
    domain/
      User.java
    persistence/
      UserEntity.java
      UserRepository.java
      RefreshTokenEntity.java
      RefreshTokenRepository.java
      VerificationTokenEntity.java
      VerificationTokenRepository.java
      PasswordResetTokenEntity.java
      PasswordResetTokenRepository.java
    service/
      UserService.java
      UserServiceImpl.java
      AuthService.java
      AuthServiceImpl.java
      EmailAlreadyTakenException.java
      UsernameAlreadyTakenException.java
      InvalidCredentialsException.java
      InvalidTokenException.java
      TokenReusedException.java
      UserNotFoundException.java
      PasswordPolicyViolationException.java
      UsernamePolicyViolationException.java
    api/
      AuthController.java
      UserController.java
      RegisterRequest.java
      LoginRequest.java
      RefreshRequest.java
      LogoutRequest.java
      VerifyEmailRequest.java
      RequestPasswordResetRequest.java
      ResetPasswordRequest.java
      AuthTokensResponse.java
      UserResponse.java
  config/
    SecurityConfig.java
    MailConfig.java
src/test/java/com/remi/
  auth/jwt/JwtServiceImplTest.java
  auth/password/PasswordValidatorTest.java
  auth/password/UsernameValidatorTest.java
  auth/password/EmailNormalizerTest.java
  user/service/UserServiceIT.java
  user/service/AuthServiceIT.java
  user/api/AuthApiE2ETest.java
  user/api/MockMailServiceTestConfig.java
```

---

## Phase A — Setup (deps, migration, config)

### Task A1: Add new Maven dependencies

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add dependencies inside `<dependencies>` block**

Add after the existing `hypersistence-utils` dependency and before the test dependencies block:

```xml
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-mail</artifactId></dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId><version>0.12.6</version>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><version>0.12.6</version><scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId><artifactId>jjwt-jackson</artifactId><version>0.12.6</version><scope>runtime</scope>
    </dependency>
```

Add inside the test dependencies block:

```xml
    <dependency>
      <groupId>org.springframework.security</groupId><artifactId>spring-security-test</artifactId><scope>test</scope>
    </dependency>
```

- [ ] **Step 2: Verify Maven resolves**

Run: `mvn -q -DskipTests dependency:resolve`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add Spring Security, jjwt, Spring Mail dependencies for Stage 2"
```

---

### Task A2: Flyway V2 migration (users + token tables)

**Files:**
- Create: `src/main/resources/db/migration/V2__init_users.sql`

- [ ] **Step 1: Write migration**

```sql
CREATE TABLE users (
  id                  UUID PRIMARY KEY,
  email               VARCHAR(254) NOT NULL UNIQUE,
  email_normalized    VARCHAR(254) NOT NULL UNIQUE,
  username            VARCHAR(20)  NOT NULL,
  username_normalized VARCHAR(20)  NOT NULL UNIQUE,
  password_hash       VARCHAR(60)  NOT NULL,
  email_verified      BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
  id            UUID PRIMARY KEY,
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  expires_at    TIMESTAMPTZ NOT NULL,
  revoked_at    TIMESTAMPTZ,
  replaced_by   UUID,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX refresh_tokens_user_active_idx ON refresh_tokens(user_id) WHERE revoked_at IS NULL;

CREATE TABLE verification_tokens (
  id            UUID PRIMARY KEY,
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  expires_at    TIMESTAMPTZ NOT NULL,
  used_at       TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE password_reset_tokens (
  id            UUID PRIMARY KEY,
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  expires_at    TIMESTAMPTZ NOT NULL,
  used_at       TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/db/migration/V2__init_users.sql
git commit -m "feat: Flyway V2 — users + refresh/verification/password_reset token tables"
```

---

### Task A3: Application config additions

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application-test.yml`

- [ ] **Step 1: Append to `application.yml` (after the existing keys)**

```yaml
spring:
  mail:
    host: ${SMTP_HOST:localhost}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USER:}
    password: ${SMTP_PASS:}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true

jwt:
  secret: ${JWT_SECRET:}
  access-ttl: PT15M
  refresh-ttl: P30D

mail:
  from: ${MAIL_FROM:noreply@remi.local}
  verification-link-base: ${MAIL_VERIFICATION_LINK_BASE:http://localhost:8080/verify}
  reset-link-base: ${MAIL_RESET_LINK_BASE:http://localhost:8080/reset}
```

- [ ] **Step 2: Append to `application-test.yml`**

```yaml
jwt:
  secret: test-only-secret-must-be-at-least-256-bits-long-or-jjwt-will-complain-x
  access-ttl: PT15M
  refresh-ttl: P30D

mail:
  from: test@remi.local
  verification-link-base: http://localhost:8080/test/verify
  reset-link-base: http://localhost:8080/test/reset
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/application.yml src/test/resources/application-test.yml
git commit -m "feat: app config for JWT + SMTP + email link bases (env-driven)"
```

---

## Phase B — Domain records + validators

### Task B1: Domain records (`User`, `AuthTokens`, `JwtClaims`)

**Files:**
- Create: `src/main/java/com/remi/user/domain/User.java`
- Create: `src/main/java/com/remi/auth/domain/AuthTokens.java`
- Create: `src/main/java/com/remi/auth/domain/JwtClaims.java`

- [ ] **Step 1: Write records**

```java
package com.remi.user.domain;

import java.time.Instant;
import java.util.UUID;

public record User(UUID id, String email, String username, boolean emailVerified, Instant createdAt) {}
```

```java
package com.remi.auth.domain;

import java.time.Instant;

public record AuthTokens(String accessToken, String refreshToken, Instant accessExpiresAt) {}
```

```java
package com.remi.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record JwtClaims(UUID userId, String email, String username, Instant issuedAt, Instant expiresAt) {}
```

- [ ] **Step 2: Compile + commit**

```bash
mvn -q compile
git add src/main/java/com/remi/user/domain/ src/main/java/com/remi/auth/domain/
git commit -m "feat(user,auth): add User, AuthTokens, JwtClaims domain records"
```

---

### Task B2: `EmailNormalizer` + TDD

**Files:**
- Create: `src/main/java/com/remi/auth/password/EmailNormalizer.java`
- Create: `src/test/java/com/remi/auth/password/EmailNormalizerTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.remi.auth.password;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EmailNormalizerTest {
  @Test void lowercasesEntireEmail() {
    assertThat(EmailNormalizer.normalize("Foo@Bar.COM")).isEqualTo("foo@bar.com");
  }
  @Test void trimsWhitespace() {
    assertThat(EmailNormalizer.normalize("  user@example.org  ")).isEqualTo("user@example.org");
  }
  @Test void preservesPlusAndDots() {
    assertThat(EmailNormalizer.normalize("u.s.er+tag@example.com")).isEqualTo("u.s.er+tag@example.com");
  }
  @Test void nullThrows() {
    org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> EmailNormalizer.normalize(null));
  }
}
```

- [ ] **Step 2: Run (FAIL — class missing)**

Run: `mvn -q test -Dtest=EmailNormalizerTest`
Expected: COMPILATION FAILURE.

- [ ] **Step 3: Write `EmailNormalizer.java`**

```java
package com.remi.auth.password;

import java.util.Locale;
import java.util.Objects;

public final class EmailNormalizer {
  private EmailNormalizer() {}
  public static String normalize(String email) {
    Objects.requireNonNull(email, "email");
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
```

- [ ] **Step 4: Run (PASS — 4 tests)**

Run: `mvn -q test -Dtest=EmailNormalizerTest`
Expected: BUILD SUCCESS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/remi/auth/password/EmailNormalizer.java src/test/java/com/remi/auth/password/EmailNormalizerTest.java
git commit -m "feat(auth): EmailNormalizer (lowercase + trim)"
```

---

### Task B3: `PasswordValidator` + TDD

**Files:**
- Create: `src/main/java/com/remi/user/service/PasswordPolicyViolationException.java`
- Create: `src/main/java/com/remi/auth/password/PasswordValidator.java`
- Create: `src/test/java/com/remi/auth/password/PasswordValidatorTest.java`

- [ ] **Step 1: Write exception first**

```java
package com.remi.user.service;
public class PasswordPolicyViolationException extends RuntimeException {
  public PasswordPolicyViolationException(String msg) { super(msg); }
}
```

- [ ] **Step 2: Write failing test**

```java
package com.remi.auth.password;

import com.remi.user.service.PasswordPolicyViolationException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PasswordValidatorTest {
  @Test void exactly10CharsIsValid() {
    assertThatNoException().isThrownBy(() -> PasswordValidator.validate("1234567890"));
  }
  @Test void nineCharsRejected() {
    assertThatThrownBy(() -> PasswordValidator.validate("123456789"))
        .isInstanceOf(PasswordPolicyViolationException.class);
  }
  @Test void emptyRejected() {
    assertThatThrownBy(() -> PasswordValidator.validate(""))
        .isInstanceOf(PasswordPolicyViolationException.class);
  }
  @Test void nullRejected() {
    assertThatThrownBy(() -> PasswordValidator.validate(null))
        .isInstanceOf(PasswordPolicyViolationException.class);
  }
  @Test void longPasswordValid() {
    assertThatNoException().isThrownBy(() -> PasswordValidator.validate("a".repeat(200)));
  }
}
```

- [ ] **Step 3: Run (FAIL)**

Run: `mvn -q test -Dtest=PasswordValidatorTest`
Expected: COMPILATION FAILURE.

- [ ] **Step 4: Write `PasswordValidator.java`**

```java
package com.remi.auth.password;

import com.remi.user.service.PasswordPolicyViolationException;

public final class PasswordValidator {
  private static final int MIN_LENGTH = 10;
  private static final int MAX_LENGTH = 200;
  private PasswordValidator() {}
  public static void validate(String password) {
    if (password == null || password.length() < MIN_LENGTH) {
      throw new PasswordPolicyViolationException("Parola trebuie să aibă cel puțin " + MIN_LENGTH + " caractere.");
    }
    if (password.length() > MAX_LENGTH) {
      throw new PasswordPolicyViolationException("Parola este prea lungă (max " + MAX_LENGTH + ").");
    }
  }
}
```

- [ ] **Step 5: Run (PASS — 5 tests)** + **Commit**

```bash
mvn -q test -Dtest=PasswordValidatorTest
git add src/main/java/com/remi/user/service/PasswordPolicyViolationException.java \
        src/main/java/com/remi/auth/password/PasswordValidator.java \
        src/test/java/com/remi/auth/password/PasswordValidatorTest.java
git commit -m "feat(auth): PasswordValidator (>=10 chars, <=200)"
```

---

### Task B4: `UsernameValidator` + TDD

**Files:**
- Create: `src/main/java/com/remi/user/service/UsernamePolicyViolationException.java`
- Create: `src/main/java/com/remi/auth/password/UsernameValidator.java`
- Create: `src/test/java/com/remi/auth/password/UsernameValidatorTest.java`

- [ ] **Step 1: Write exception**

```java
package com.remi.user.service;
public class UsernamePolicyViolationException extends RuntimeException {
  public UsernamePolicyViolationException(String msg) { super(msg); }
}
```

- [ ] **Step 2: Write failing test**

```java
package com.remi.auth.password;

import com.remi.user.service.UsernamePolicyViolationException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UsernameValidatorTest {
  @Test void threeCharsValid() {
    assertThatNoException().isThrownBy(() -> UsernameValidator.validate("abc"));
  }
  @Test void twentyCharsValid() {
    assertThatNoException().isThrownBy(() -> UsernameValidator.validate("a".repeat(20)));
  }
  @Test void twoCharsRejected() {
    assertThatThrownBy(() -> UsernameValidator.validate("ab"))
        .isInstanceOf(UsernamePolicyViolationException.class);
  }
  @Test void twentyOneCharsRejected() {
    assertThatThrownBy(() -> UsernameValidator.validate("a".repeat(21)))
        .isInstanceOf(UsernamePolicyViolationException.class);
  }
  @Test void alphanumericPlusUnderscoreDashValid() {
    assertThatNoException().isThrownBy(() -> UsernameValidator.validate("user_123-abc"));
  }
  @Test void spaceRejected() {
    assertThatThrownBy(() -> UsernameValidator.validate("user 123"))
        .isInstanceOf(UsernamePolicyViolationException.class);
  }
  @Test void unicodeRejected() {
    assertThatThrownBy(() -> UsernameValidator.validate("usér"))
        .isInstanceOf(UsernamePolicyViolationException.class);
  }
  @Test void nullRejected() {
    assertThatThrownBy(() -> UsernameValidator.validate(null))
        .isInstanceOf(UsernamePolicyViolationException.class);
  }
}
```

- [ ] **Step 3: Run (FAIL)**

Run: `mvn -q test -Dtest=UsernameValidatorTest`

- [ ] **Step 4: Write `UsernameValidator.java`**

```java
package com.remi.auth.password;

import com.remi.user.service.UsernamePolicyViolationException;
import java.util.regex.Pattern;

public final class UsernameValidator {
  private static final int MIN_LENGTH = 3;
  private static final int MAX_LENGTH = 20;
  private static final Pattern ALLOWED = Pattern.compile("^[a-zA-Z0-9_-]+$");
  private UsernameValidator() {}
  public static void validate(String username) {
    if (username == null || username.length() < MIN_LENGTH || username.length() > MAX_LENGTH) {
      throw new UsernamePolicyViolationException("Username-ul trebuie să aibă între " + MIN_LENGTH + " și " + MAX_LENGTH + " caractere.");
    }
    if (!ALLOWED.matcher(username).matches()) {
      throw new UsernamePolicyViolationException("Username-ul poate conține doar litere, cifre, underscore și liniuță.");
    }
  }
}
```

- [ ] **Step 5: Run (PASS — 8 tests)** + **Commit**

```bash
mvn -q test -Dtest=UsernameValidatorTest
git add src/main/java/com/remi/user/service/UsernamePolicyViolationException.java \
        src/main/java/com/remi/auth/password/UsernameValidator.java \
        src/test/java/com/remi/auth/password/UsernameValidatorTest.java
git commit -m "feat(auth): UsernameValidator (3-20 chars, alphanumeric + _ -)"
```

---

## Phase C — JWT

### Task C1: `JwtService` interface + `JwtServiceImpl` + TDD

**Files:**
- Create: `src/main/java/com/remi/auth/jwt/JwtService.java`
- Create: `src/main/java/com/remi/auth/jwt/JwtServiceImpl.java`
- Create: `src/test/java/com/remi/auth/jwt/JwtServiceImplTest.java`

- [ ] **Step 1: Write interface**

```java
package com.remi.auth.jwt;

import com.remi.auth.domain.JwtClaims;
import com.remi.user.domain.User;

public interface JwtService {
  String issueAccessToken(User user);
  JwtClaims parseAccessToken(String token);
}
```

- [ ] **Step 2: Write failing test**

```java
package com.remi.auth.jwt;

import com.remi.auth.domain.JwtClaims;
import com.remi.user.domain.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class JwtServiceImplTest {
  private static final String SECRET = "test-only-secret-must-be-at-least-256-bits-long-or-jjwt-will-complain-x";
  private final JwtServiceImpl svc = new JwtServiceImpl(SECRET, Duration.ofMinutes(15), Clock.systemUTC());
  private final User user = new User(UUID.randomUUID(), "u@e.com", "user", true, Instant.now());

  @Test void roundTripPreservesClaims() {
    String token = svc.issueAccessToken(user);
    JwtClaims c = svc.parseAccessToken(token);
    assertThat(c.userId()).isEqualTo(user.id());
    assertThat(c.email()).isEqualTo(user.email());
    assertThat(c.username()).isEqualTo(user.username());
    assertThat(c.expiresAt()).isAfter(Instant.now());
  }

  @Test void parseWithWrongSecretThrows() {
    String token = svc.issueAccessToken(user);
    JwtServiceImpl other = new JwtServiceImpl(
        "different-secret-also-256-bits-long-just-different-from-the-original",
        Duration.ofMinutes(15), Clock.systemUTC());
    assertThatThrownBy(() -> other.parseAccessToken(token)).isInstanceOf(JwtException.class);
  }

  @Test void expiredTokenThrows() {
    Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
    JwtServiceImpl old = new JwtServiceImpl(SECRET, Duration.ofMinutes(15), past);
    String token = old.issueAccessToken(user);
    assertThatThrownBy(() -> svc.parseAccessToken(token)).isInstanceOf(ExpiredJwtException.class);
  }

  @Test void malformedTokenThrows() {
    assertThatThrownBy(() -> svc.parseAccessToken("not.a.token")).isInstanceOf(MalformedJwtException.class);
  }

  @Test void shortSecretRejectedAtConstruction() {
    assertThatThrownBy(() -> new JwtServiceImpl("short", Duration.ofMinutes(15), Clock.systemUTC()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
```

- [ ] **Step 3: Run (FAIL)**

Run: `mvn -q test -Dtest=JwtServiceImplTest`
Expected: COMPILATION FAILURE.

- [ ] **Step 4: Write `JwtServiceImpl.java`**

```java
package com.remi.auth.jwt;

import com.remi.auth.domain.JwtClaims;
import com.remi.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtServiceImpl implements JwtService {
  private final SecretKey key;
  private final Duration accessTtl;
  private final Clock clock;

  public JwtServiceImpl(@Value("${jwt.secret}") String secret,
                        @Value("${jwt.access-ttl}") Duration accessTtl,
                        Clock clock) {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalArgumentException("jwt.secret must be at least 32 bytes (256 bits)");
    }
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTtl = accessTtl;
    this.clock = clock;
  }

  @Override
  public String issueAccessToken(User user) {
    Instant now = clock.instant();
    Instant exp = now.plus(accessTtl);
    return Jwts.builder()
        .subject(user.id().toString())
        .claim("email", user.email())
        .claim("username", user.username())
        .issuedAt(Date.from(now))
        .expiration(Date.from(exp))
        .signWith(key)
        .compact();
  }

  @Override
  public JwtClaims parseAccessToken(String token) {
    Claims c = Jwts.parser().verifyWith(key).build()
        .parseSignedClaims(token).getPayload();
    return new JwtClaims(
        UUID.fromString(c.getSubject()),
        c.get("email", String.class),
        c.get("username", String.class),
        c.getIssuedAt().toInstant(),
        c.getExpiration().toInstant()
    );
  }
}
```

- [ ] **Step 5: Add `Clock` bean** to a new `src/main/java/com/remi/config/ClockConfig.java`:

```java
package com.remi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class ClockConfig {
  @Bean public Clock clock() { return Clock.systemUTC(); }
}
```

- [ ] **Step 6: Run (PASS — 5 tests)** + **Commit**

```bash
mvn -q test -Dtest=JwtServiceImplTest
git add src/main/java/com/remi/auth/jwt/JwtService.java \
        src/main/java/com/remi/auth/jwt/JwtServiceImpl.java \
        src/main/java/com/remi/config/ClockConfig.java \
        src/test/java/com/remi/auth/jwt/JwtServiceImplTest.java
git commit -m "feat(auth): JwtService (HS256, issue/parse with clock injection)"
```

---

### Task C2: `JwtAuthFilter` + `JsonAuthenticationEntryPoint`

**Files:**
- Create: `src/main/java/com/remi/auth/jwt/JwtAuthFilter.java`
- Create: `src/main/java/com/remi/auth/jwt/JsonAuthenticationEntryPoint.java`

These are wired into Spring Security in Task G1; they need to exist first.

- [ ] **Step 1: Write `JwtAuthFilter.java`**

```java
package com.remi.auth.jwt;

import com.remi.auth.domain.JwtClaims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
  public static final String EXPIRED_HEADER = "X-Token-Expired";
  private final JwtService jwt;

  public JwtAuthFilter(JwtService jwt) { this.jwt = jwt; }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
      throws ServletException, IOException {
    String header = req.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring("Bearer ".length());
      try {
        JwtClaims claims = jwt.parseAccessToken(token);
        var auth = new UsernamePasswordAuthenticationToken(claims.userId(), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (ExpiredJwtException e) {
        resp.setHeader(EXPIRED_HEADER, "true");
        SecurityContextHolder.clearContext();
      } catch (JwtException e) {
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(req, resp);
  }
}
```

- [ ] **Step 2: Write `JsonAuthenticationEntryPoint.java`**

```java
package com.remi.auth.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Map;

@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {
  private final ObjectMapper om;
  public JsonAuthenticationEntryPoint(ObjectMapper om) { this.om = om; }

  @Override
  public void commence(HttpServletRequest req, HttpServletResponse resp, AuthenticationException e)
      throws IOException {
    String code = "true".equals(resp.getHeader(JwtAuthFilter.EXPIRED_HEADER)) ? "TOKEN_EXPIRED" : "UNAUTHORIZED";
    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
    om.writeValue(resp.getOutputStream(), Map.of("code", code, "message", "Autentificare necesară."));
  }
}
```

- [ ] **Step 3: Compile + commit**

```bash
mvn -q compile
git add src/main/java/com/remi/auth/jwt/JwtAuthFilter.java \
        src/main/java/com/remi/auth/jwt/JsonAuthenticationEntryPoint.java
git commit -m "feat(auth): JwtAuthFilter + JsonAuthenticationEntryPoint"
```

---

## Phase D — Mail

### Task D1: `MailService` interface + `SmtpMailService` impl

**Files:**
- Create: `src/main/java/com/remi/auth/mail/MailService.java`
- Create: `src/main/java/com/remi/auth/mail/SmtpMailService.java`

- [ ] **Step 1: Write interface**

```java
package com.remi.auth.mail;

import java.util.UUID;

public interface MailService {
  void sendVerification(String toEmail, String username, UUID verificationToken);
  void sendPasswordReset(String toEmail, String username, UUID resetToken);
}
```

- [ ] **Step 2: Write `SmtpMailService.java`**

```java
package com.remi.auth.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@Profile("!test")
public class SmtpMailService implements MailService {
  private final JavaMailSender sender;
  private final String from;
  private final String verifyBase;
  private final String resetBase;

  public SmtpMailService(JavaMailSender sender,
                         @Value("${mail.from}") String from,
                         @Value("${mail.verification-link-base}") String verifyBase,
                         @Value("${mail.reset-link-base}") String resetBase) {
    this.sender = sender;
    this.from = from;
    this.verifyBase = verifyBase;
    this.resetBase = resetBase;
  }

  @Override
  public void sendVerification(String toEmail, String username, UUID token) {
    SimpleMailMessage msg = new SimpleMailMessage();
    msg.setFrom(from);
    msg.setTo(toEmail);
    msg.setSubject("Verifică adresa de email — Remi");
    msg.setText("Salut " + username + ",\n\nClick pentru a-ți verifica emailul: "
        + verifyBase + "?token=" + token + "\n\nLinkul expiră în 24 ore.");
    sender.send(msg);
  }

  @Override
  public void sendPasswordReset(String toEmail, String username, UUID token) {
    SimpleMailMessage msg = new SimpleMailMessage();
    msg.setFrom(from);
    msg.setTo(toEmail);
    msg.setSubject("Resetare parolă — Remi");
    msg.setText("Salut " + username + ",\n\nClick pentru a-ți reseta parola: "
        + resetBase + "?token=" + token + "\n\nLinkul expiră în 1 oră. Dacă nu ai cerut tu, ignoră acest email.");
    sender.send(msg);
  }
}
```

- [ ] **Step 3: Compile + commit**

```bash
mvn -q compile
git add src/main/java/com/remi/auth/mail/
git commit -m "feat(auth): MailService interface + SmtpMailService impl"
```

---

## Phase E — Persistence (entities + repositories)

### Task E1: `UserEntity` + `UserRepository`

**Files:**
- Create: `src/main/java/com/remi/user/persistence/UserEntity.java`
- Create: `src/main/java/com/remi/user/persistence/UserRepository.java`

- [ ] **Step 1: Write `UserEntity.java`**

```java
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
```

- [ ] **Step 2: Write `UserRepository.java`**

```java
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
```

- [ ] **Step 3: Compile + commit**

```bash
mvn -q compile
git add src/main/java/com/remi/user/persistence/UserEntity.java \
        src/main/java/com/remi/user/persistence/UserRepository.java
git commit -m "feat(user): UserEntity + UserRepository"
```

---

### Task E2: Token entities + repositories (refresh, verification, password reset)

**Files:**
- Create: `src/main/java/com/remi/user/persistence/RefreshTokenEntity.java`
- Create: `src/main/java/com/remi/user/persistence/RefreshTokenRepository.java`
- Create: `src/main/java/com/remi/user/persistence/VerificationTokenEntity.java`
- Create: `src/main/java/com/remi/user/persistence/VerificationTokenRepository.java`
- Create: `src/main/java/com/remi/user/persistence/PasswordResetTokenEntity.java`
- Create: `src/main/java/com/remi/user/persistence/PasswordResetTokenRepository.java`

- [ ] **Step 1: Write `RefreshTokenEntity.java`**

```java
package com.remi.user.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {
  @Id private UUID id;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;
  @Column(name = "revoked_at") private Instant revokedAt;
  @Column(name = "replaced_by") private UUID replacedBy;
  @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

  protected RefreshTokenEntity() {}
  public RefreshTokenEntity(UUID id, UUID userId, Instant expiresAt) {
    this.id = id; this.userId = userId; this.expiresAt = expiresAt;
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public Instant getExpiresAt() { return expiresAt; }
  public Instant getRevokedAt() { return revokedAt; }
  public UUID getReplacedBy() { return replacedBy; }
  public Instant getCreatedAt() { return createdAt; }

  public void revoke(Instant when) { this.revokedAt = when; }
  public void rotate(Instant when, UUID newId) { this.revokedAt = when; this.replacedBy = newId; }
}
```

- [ ] **Step 2: Write `RefreshTokenRepository.java`**

```java
package com.remi.user.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
  @Modifying
  @Query("UPDATE RefreshTokenEntity r SET r.revokedAt = :when WHERE r.userId = :userId AND r.revokedAt IS NULL")
  int revokeAllActiveForUser(@Param("userId") UUID userId, @Param("when") Instant when);
}
```

- [ ] **Step 3: Write `VerificationTokenEntity.java` + repo**

```java
package com.remi.user.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_tokens")
public class VerificationTokenEntity {
  @Id private UUID id;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;
  @Column(name = "used_at") private Instant usedAt;
  @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

  protected VerificationTokenEntity() {}
  public VerificationTokenEntity(UUID id, UUID userId, Instant expiresAt) {
    this.id = id; this.userId = userId; this.expiresAt = expiresAt;
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public Instant getExpiresAt() { return expiresAt; }
  public Instant getUsedAt() { return usedAt; }
  public void markUsed(Instant when) { this.usedAt = when; }
}
```

```java
package com.remi.user.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface VerificationTokenRepository extends JpaRepository<VerificationTokenEntity, UUID> {}
```

- [ ] **Step 4: Write `PasswordResetTokenEntity.java` + repo**

```java
package com.remi.user.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetTokenEntity {
  @Id private UUID id;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;
  @Column(name = "used_at") private Instant usedAt;
  @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

  protected PasswordResetTokenEntity() {}
  public PasswordResetTokenEntity(UUID id, UUID userId, Instant expiresAt) {
    this.id = id; this.userId = userId; this.expiresAt = expiresAt;
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public Instant getExpiresAt() { return expiresAt; }
  public Instant getUsedAt() { return usedAt; }
  public void markUsed(Instant when) { this.usedAt = when; }
}
```

```java
package com.remi.user.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {}
```

- [ ] **Step 5: Compile + commit**

```bash
mvn -q compile
git add src/main/java/com/remi/user/persistence/
git commit -m "feat(user): RefreshToken, VerificationToken, PasswordResetToken entities + repos"
```

---

## End of Part 1

Continuă cu Part 2 (`2026-05-16-stage2-auth-part2.md`) — Phases F (services), G (security config + exceptions), H (controllers), I (E2E + final).
