package com.remi.user.service;

import com.remi.user.domain.User;
import java.util.UUID;

public interface UserService {
  User register(String email, String username, String rawPassword);
  void verifyEmail(UUID token);
  void requestPasswordReset(String email);
  void resetPassword(UUID token, String newRawPassword);
  User getById(UUID id);
  User getByEmail(String email);
}
