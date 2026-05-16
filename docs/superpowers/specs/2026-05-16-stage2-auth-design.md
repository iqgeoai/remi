# Stage 2 — Auth + Users (Design)

**Data:** 2026-05-16
**Status:** Approved
**Scope:** Stage 2 din planul multi-etape Remi multiplayer. Vezi `2026-05-16-stage1-game-engine-design.md` pentru roadmap.

## Context

Stage 1 a livrat engine + persistență + REST dev (endpoint-uri `/api/dev/games/*` deschise, fără auth). Stage 2 adaugă identitate de utilizator: înregistrare, verificare email, login, refresh tokens, password reset. Nu face linking user↔game (asta vine în Stage 3 cu multiplayer).

Endpoint-urile `/api/dev/*` rămân deschise pentru compatibilitate cu jocuri în desfășurare; vor fi înlocuite în Stage 3.

## Cerințe (confirmate cu user)

- **Scope complet**: register + email verification + login + password reset (fără social login)
- **Email delivery**: SMTP real prin env vars; mock `JavaMailSender` în teste
- **JWT**: Access (15min) + Refresh (30 zile, opaque UUID în DB, one-time-use rotation)
- **Security stack**: Spring Security + JWT filter custom (`OncePerRequestFilter`)
- **User model**: email separat de username (ambele unique, case-insensitive)

Decizii implicite (fără întrebare explicită):
- **JWT signing**: HS256 cu shared secret în env (`JWT_SECRET`, min 256 bits)
- **Password rules**: min 10 chars, fără reguli de complexitate (per NIST 800-63B)
- **Username rules**: 3-20 chars, `^[a-zA-Z0-9_-]+$`, case-insensitive unique
- **Failed login lockout**: niciunul în Stage 2 (rate limit vine în Stage 8)
- **Refresh token rotation**: one-time use cu reuse detection (best practice)
- **TTL tokens email**: 24h pentru verification, 1h pentru password reset

## Stack tehnic (adăugat la Stage 1)

- `spring-boot-starter-security`
- `io.jsonwebtoken:jjwt-api` + `jjwt-impl` + `jjwt-jackson` 0.12.x
- `spring-boot-starter-mail`
- `org.springframework.security:spring-security-test`

Restul stack-ului rămâne identic: Java 21, Spring Boot 3.3.5, Maven, Postgres 16, Flyway, Jackson, JUnit 5, AssertJ, Testcontainers, JaCoCo.

## 1. Arhitectură

Pachete noi (toate în afara `com.remi.engine.*` care rămâne pur):

```
com.remi.auth
  .domain          ← AuthTokens, JwtClaims (records)
  .password        ← PasswordEncoder bean (BCrypt strength 12), PasswordValidator
  .jwt             ← JwtService, JwtAuthFilter
  .mail            ← MailService interface, SmtpMailService impl, MockMailService (test)
com.remi.user
  .domain          ← User record
  .persistence     ← UserEntity, UserRepository, RefreshTokenEntity/Repo,
                     VerificationTokenEntity/Repo, PasswordResetTokenEntity/Repo
  .service         ← UserService, AuthService
  .api             ← AuthController, UserController, request/response DTOs
com.remi.config
  .SecurityConfig  ← SecurityFilterChain (stateless, JWT filter, public whitelist)
  .MailConfig      ← JavaMailSender from env; no-op in @Profile("test")
```

**Regulă păstrată:** `com.remi.engine.*` zero importuri Spring/JPA. Auth e separat în `com.remi.auth` + `com.remi.user`.

**Migrare DB nouă:** `V2__init_users.sql` adaugă 4 tabele. `games` rămâne neatins.

## 2. Componente

### 2.1 Schema DB (Flyway V2)

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
CREATE INDEX refresh_tokens_user_idx ON refresh_tokens(user_id) WHERE revoked_at IS NULL;

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

`email_normalized` / `username_normalized` separate pentru lookup case-insensitive păstrând casing-ul original pentru afișare.

### 2.2 Domain records

```java
public record AuthTokens(String accessToken, String refreshToken, Instant accessExpiresAt) {}
public record JwtClaims(UUID userId, String email, String username, Instant issuedAt, Instant expiresAt) {}
public record User(UUID id, String email, String username, boolean emailVerified, Instant createdAt) {}
```

### 2.3 Service interfaces

```java
public interface UserService {
  User register(String email, String username, String rawPassword);
  void verifyEmail(UUID token);
  void requestPasswordReset(String email);
  void resetPassword(UUID token, String newRawPassword);
  User getById(UUID id);
  User getByEmail(String email);
}

public interface AuthService {
  AuthTokens login(String emailOrUsername, String rawPassword);
  AuthTokens refresh(UUID refreshTokenId);
  void logout(UUID refreshTokenId);
  void logoutAll(UUID userId);    // internal-only: called by password reset and refresh-reuse detection; no HTTP endpoint in Stage 2
}

public interface JwtService {
  String issueAccessToken(User user);
  JwtClaims parseAccessToken(String token);  // throws JwtException
}

public interface MailService {
  void sendVerification(String toEmail, String username, UUID verificationToken);
  void sendPasswordReset(String toEmail, String username, UUID resetToken);
}
```

### 2.4 REST API

```
POST /api/auth/register             {email, username, password}     → 201
POST /api/auth/verify-email         {token}                          → 204
POST /api/auth/login                {emailOrUsername, password}     → 200 + AuthTokens
POST /api/auth/refresh              {refreshToken}                  → 200 + AuthTokens
POST /api/auth/logout               {refreshToken}                  → 204
POST /api/auth/request-password-reset  {email}                      → 204 (always)
POST /api/auth/reset-password       {token, newPassword}            → 204
GET  /api/users/me                  (Bearer)                         → 200 + User
```

### 2.5 Security config

```java
@Configuration @EnableWebSecurity
public class SecurityConfig {
  @Bean SecurityFilterChain chain(HttpSecurity http, JwtAuthFilter jwtFilter) throws Exception {
    return http
      .csrf(c -> c.disable())
      .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
      .authorizeHttpRequests(a -> a
        .requestMatchers("/api/auth/**").permitAll()
        .requestMatchers("/api/dev/**").permitAll()
        .anyRequest().authenticated())
      .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
      .exceptionHandling(e -> e.authenticationEntryPoint(jsonAuthEntryPoint))
      .build();
  }
  @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
}
```

`JwtAuthFilter` extinde `OncePerRequestFilter`: extrage `Authorization: Bearer <token>`, validează via `JwtService.parseAccessToken`, populează `SecurityContext` cu `UsernamePasswordAuthenticationToken(userId, null, [])`.

## 3. Data flow

### 3.1 Register

```
POST /api/auth/register → validate (Bean Validation) → UserService.register:
  1. normalize email/username lowercase
  2. uniqueness check → EmailAlreadyTakenException / UsernameAlreadyTakenException (409)
  3. BCrypt hash password
  4. INSERT users (email_verified=false)
  5. INSERT verification_tokens (expires=now+24h)
  6. MailService.sendVerification (synchronous in Stage 2)
  7. return User
→ HTTP 201
```

### 3.2 Verify email

```
POST /api/auth/verify-email {token}:
  - lookup verification_tokens
  - reject if not found / expired / already used → InvalidTokenException (400)
  - UPDATE verification_tokens SET used_at=now
  - UPDATE users SET email_verified=true
→ HTTP 204
```

### 3.3 Login

```
POST /api/auth/login → AuthService.login:
  1. lookup by email_normalized; fallback username_normalized
  2. user not found → InvalidCredentialsException
  3. password mismatch → InvalidCredentialsException
  4. !emailVerified → InvalidCredentialsException (same exception, no enumeration)
  5. issueAccessToken (HS256, exp=now+15min, claims {sub:userId, email, username})
  6. INSERT refresh_tokens (id=random UUID, expires=now+30d)
  7. return AuthTokens
→ HTTP 200
```

Toate căile de eșec aruncă același `InvalidCredentialsException` cu mesaj "Credențiale invalide sau email neverificat." — previne enumerarea conturilor.

### 3.4 Authenticated request

```
GET /api/users/me (Bearer):
  JwtAuthFilter → extract token → JwtService.parseAccessToken → JwtClaims
  on success: SecurityContext gets UsernamePasswordAuthenticationToken(userId, null, [])
  on JwtException: clear context → 401 via authenticationEntryPoint
  UserController.me(@AuthenticationPrincipal UUID userId) → userService.getById(userId)
→ HTTP 200
```

Pe expiry: 401 cu body `{code:"TOKEN_EXPIRED"}` (header `X-Token-Expired:true` setat de filter). Client face `/refresh`.

### 3.5 Refresh cu rotation + reuse detection

```
POST /api/auth/refresh → AuthService.refresh:
  1. SELECT refresh_tokens WHERE id=token
  2. not found → InvalidTokenException
  3. revoked_at != null:
       a. replaced_by != null → REUSE → revoke ALL active tokens for user → TokenReusedException (401)
       b. else → InvalidTokenException (logout-revoked)
  4. expires_at < now → InvalidTokenException
  5. issue new pair (access + refresh)
  6. UPDATE old: revoked_at=now, replaced_by=newId
  7. return new AuthTokens
→ HTTP 200
```

### 3.6 Password reset

```
Request (POST /api/auth/request-password-reset {email}):
  - lookup by email_normalized
  - found: INSERT password_reset_tokens (expires=now+1h) + sendPasswordReset
  - not found: log INFO, no error
→ HTTP 204 always (no enumeration)

Reset (POST /api/auth/reset-password {token, newPassword}):
  - validate token (not found/expired/used → InvalidTokenException 400)
  - encode newPassword, UPDATE users SET password_hash
  - UPDATE password_reset_tokens SET used_at=now
  - REVOKE all active refresh_tokens for user (force re-login everywhere)
→ HTTP 204
```

### 3.7 Logout

```
POST /api/auth/logout {refreshToken}:
  UPDATE refresh_tokens SET revoked_at=now WHERE id=token AND revoked_at IS NULL
→ HTTP 204
```

Access token rămâne valid maxim 15 min (stateless JWT, fără DB lookup). Trade-off acceptat: scurtăm TTL access la 15min.

### 3.8 Email pe SMTP

`MailService` real folosește `JavaMailSender` injectat din `spring-boot-starter-mail`. Config via env:
```
SMTP_HOST=smtp.mailtrap.io
SMTP_PORT=587
SMTP_USER=...
SMTP_PASS=...
MAIL_FROM=noreply@remi.example
MAIL_VERIFICATION_LINK_BASE=https://app.remi.example/verify
MAIL_RESET_LINK_BASE=https://app.remi.example/reset
```

În test profile, `MailConfig` expune `MailService` mock care înregistrează apelurile în listă in-memory.

## 4. Error handling

### Excepții noi

```java
EmailAlreadyTakenException        → 409 EMAIL_TAKEN
UsernameAlreadyTakenException     → 409 USERNAME_TAKEN
InvalidCredentialsException       → 400 INVALID_CREDENTIALS (acelaș mesaj pentru toate cauzele)
InvalidTokenException(Kind)       → 400 INVALID_TOKEN (kind: VERIFICATION, REFRESH, PASSWORD_RESET)
TokenReusedException              → 401 TOKEN_REUSED (force fresh login)
UserNotFoundException             → 404 USER_NOT_FOUND
PasswordPolicyViolationException  → 400 PASSWORD_POLICY
UsernamePolicyViolationException  → 400 USERNAME_POLICY
```

### Extensii la `ApiExceptionHandler`

Adăugăm handlere pentru fiecare excepție de mai sus. Toate mesajele în română.

### Spring Security 401/403

Gestionate separat (nu prin `@RestControllerAdvice`):

```java
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {
  public void commence(req, resp, e) {
    String code = (resp.getHeader("X-Token-Expired") != null) ? "TOKEN_EXPIRED" : "UNAUTHORIZED";
    resp.setStatus(401);
    resp.setContentType("application/json");
    resp.getWriter().write(json({code, message: "Autentificare necesară."}));
  }
}
```

`JwtAuthFilter` setează `X-Token-Expired: true` când prinde `ExpiredJwtException`, permițând entry point să distingă "lipsă token" de "expirat".

### Validare request

`jakarta.validation` pe DTO-uri:
```java
public record RegisterRequest(
    @NotBlank @Email @Size(max=254) String email,
    @NotBlank @Size(min=3, max=20) @Pattern(regexp="^[a-zA-Z0-9_-]+$") String username,
    @NotBlank @Size(min=10, max=200) String password
) {}
```

`MethodArgumentNotValidException` rămâne mapată la 400 INVALID_REQUEST; extindem să returneze și câmpurile invalide (acum returnează `e.getMessage()` generic).

### Logging

- `INFO`: register, verify-email-success, login-success, password-reset-completed
- `DEBUG`: refresh-success
- `WARN`: invalid-credentials, token-reused, expired-token-on-protected-endpoint
- `ERROR`: SMTP failure, JWT signing failure

**Niciodată în log**: parolă raw, JWT semnat complet.

### Excluderi explicite (deferate)

- Rate limiting pe login (Stage 8)
- CAPTCHA pe register
- Audit log persistat
- 2FA
- Account lockout după N login-uri eșuate (Stage 8)

## 5. Testing

### Piramidă

```
E2E REST (MockMvc + Testcontainers + mock SMTP)        ~10 teste
Integration (UserService, AuthService + Testcontainers) ~15 teste
Unit (JwtService, validatori, normalizare, BCrypt)      ~25 teste
```

### Unit tests (no Spring, no DB)

- `JwtServiceTest`: round-trip claims, signature greșită → JwtException, expirat → ExpiredJwtException, malformed → MalformedJwtException, secret diferit la parse → JwtException
- `PasswordValidatorTest`: ≥10 OK, <10 reject, boundary 10
- `UsernameValidatorTest`: 3-20 chars, regex check, unicode reject
- `EmailNormalizationTest`: lowercase pentru lookup, păstrare casing pentru afișare
- `BCryptCompatibilityTest`: hash format `$2a$12$...`, 60 chars, matches() corect

### Integration tests (Spring + Testcontainers + mock SMTP)

- `UserServiceIT`: register happy + duplicates + case-insensitive uniqueness; verifyEmail (valid/expirat/used); requestPasswordReset cu email cunoscut/necunoscut; resetPassword + revocare refresh_tokens
- `AuthServiceIT`: login (correct/wrong-pass/unknown-user/unverified — toate aruncă același exception); refresh (valid/expirat/revoked/REUSE detection cu toate token-urile revocate); logout single vs logoutAll

### E2E REST (MockMvc + Testcontainers + Security activ)

- `AuthApiE2ETest`: full happy path register→verify→login→/me→refresh→/me→logout; toate path-urile de eroare cu coduri specifice; `/api/dev/games/*` rămâne accesibil fără auth (whitelist test)
- `MailIntegrationIT`: mock capturează apeluri; verifică link-uri și conținut minimal

### Property test (opțional, jqwik)

- Pentru orice parolă ≥10 chars: BCrypt encode + matches → true
- Pentru orice JWT semnat cu K1, parse cu K2≠K1 → JwtException
- Pentru orice user nou + login imediat → exact 1 refresh_token activ în DB

### Configurare test profile

`src/test/resources/application-test.yml` adăugat:
```yaml
jwt:
  secret: test-only-secret-must-be-256-bits-long-or-jjwt-will-complain-loudly-xx
  access-ttl: PT15M
  refresh-ttl: P30D
mail:
  verification-link-base: http://localhost:8080/test/verify
  reset-link-base: http://localhost:8080/test/reset
  from: test@remi.local
```

`@TestConfiguration` expune `@Bean @Primary MailService mockMailService()` ce stochează apelurile inspectabile.

### Coverage gate

Extindem regula JaCoCo:
- `com.remi.engine.*` rămâne **90%** (deja trecut)
- `com.remi.auth.*` și `com.remi.user.service.*` nou: **85%**
- Restul (controllers, config) fără gate strict
