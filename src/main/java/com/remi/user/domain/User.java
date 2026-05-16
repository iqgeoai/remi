package com.remi.user.domain;

import java.time.Instant;
import java.util.UUID;

public record User(UUID id, String email, String username, boolean emailVerified, Instant createdAt) {}
