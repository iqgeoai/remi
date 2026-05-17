package com.remi.lobby.api;
import com.remi.engine.domain.Action;
import jakarta.validation.constraints.NotNull;
public record ActionRequest(@NotNull Action action) {}
