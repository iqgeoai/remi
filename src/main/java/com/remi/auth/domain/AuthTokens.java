package com.remi.auth.domain;

import java.time.Instant;

public record AuthTokens(String accessToken, String refreshToken, Instant accessExpiresAt) {}
