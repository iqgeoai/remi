package com.remi.lobby.domain;

import com.remi.engine.domain.Difficulty;
import com.remi.engine.domain.Mode;
import java.time.Instant;
import java.util.UUID;

public record LobbyGame(
    UUID id, UUID ownerId, GameVisibility visibility, String joinCode,
    int numPlayers, Mode mode, Difficulty difficulty,
    int seatsTaken, boolean started, Instant createdAt
) {}
