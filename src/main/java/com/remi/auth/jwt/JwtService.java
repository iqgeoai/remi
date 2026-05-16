package com.remi.auth.jwt;

import com.remi.auth.domain.JwtClaims;
import com.remi.user.domain.User;

public interface JwtService {
  String issueAccessToken(User user);
  JwtClaims parseAccessToken(String token);
}
