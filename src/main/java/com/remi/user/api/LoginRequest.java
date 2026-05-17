package com.remi.user.api;
import jakarta.validation.constraints.NotBlank;
public record LoginRequest(@NotBlank String emailOrUsername, @NotBlank String password) {}
