package com.remi.user.api;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record VerifyEmailRequest(@NotNull UUID token) {}
