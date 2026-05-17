package com.remi.lobby.api;
import com.remi.engine.domain.Difficulty;
import com.remi.engine.domain.Mode;
import com.remi.lobby.domain.GameVisibility;
import jakarta.validation.constraints.*;
public record CreateGameRequest(
    @NotNull GameVisibility visibility,
    @Min(2) @Max(6) int numPlayers,
    @NotNull Mode mode,
    @NotNull Difficulty difficulty
) {}
