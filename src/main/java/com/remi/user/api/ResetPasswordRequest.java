package com.remi.user.api;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record ResetPasswordRequest(@NotNull UUID token, @NotBlank @Size(min=10, max=200) String newPassword) {}
