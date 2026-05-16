package com.remi.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record JwtClaims(UUID userId, String email, String username, Instant issuedAt, Instant expiresAt) {}
