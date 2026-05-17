package com.remi.user.service;

import com.remi.auth.domain.AuthTokens;
import java.util.UUID;

public interface AuthService {
  AuthTokens login(String emailOrUsername, String rawPassword);
  AuthTokens refresh(UUID refreshTokenId);
  void logout(UUID refreshTokenId);
  void logoutAll(UUID userId);
}
