package com.remi.lobby.api;
import jakarta.validation.constraints.NotBlank;
public record JoinByCodeRequest(@NotBlank String joinCode) {}
