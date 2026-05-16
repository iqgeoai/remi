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
