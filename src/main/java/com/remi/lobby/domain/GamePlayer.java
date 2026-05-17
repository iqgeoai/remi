package com.remi.lobby.domain;

import java.time.Instant;
import java.util.UUID;

public record GamePlayer(UUID gameId, int playerIdx, UUID userId, Instant joinedAt) {}
