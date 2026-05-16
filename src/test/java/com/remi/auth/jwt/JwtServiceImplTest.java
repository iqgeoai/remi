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
