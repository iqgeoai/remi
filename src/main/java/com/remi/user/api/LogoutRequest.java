package com.remi.user.api;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record LogoutRequest(@NotNull UUID refreshToken) {}
