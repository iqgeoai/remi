package com.remi.api;
import com.remi.engine.domain.*;
import jakarta.validation.constraints.*;
public record CreateGameRequest(
    @Min(2) @Max(6) int numPlayers,
    @NotNull Mode mode,
    @NotNull Difficulty difficulty,
    Long seed
) {}
