package com.remi.user.api;
import com.remi.auth.domain.AuthTokens;
import java.time.Instant;
public record AuthTokensResponse(String accessToken, String refreshToken, Instant accessExpiresAt) {
  public static AuthTokensResponse of(AuthTokens t) {
    return new AuthTokensResponse(t.accessToken(), t.refreshToken(), t.accessExpiresAt());
  }
}
