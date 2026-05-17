package com.remi.user.api;
import jakarta.validation.constraints.*;
public record RegisterRequest(
    @NotBlank @Email @Size(max=254) String email,
    @NotBlank @Size(min=3, max=20) @Pattern(regexp="^[a-zA-Z0-9_-]+$") String username,
    @NotBlank @Size(min=10, max=200) String password
) {}
