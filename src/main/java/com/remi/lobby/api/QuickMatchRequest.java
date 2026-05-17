package com.remi.lobby.api;
import com.remi.engine.domain.Difficulty;
import com.remi.engine.domain.Mode;
import jakarta.validation.constraints.*;
public record QuickMatchRequest(
    @Min(2) @Max(6) int numPlayers,
    @NotNull Mode mode,
    @NotNull Difficulty difficulty
) {}
