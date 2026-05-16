package com.remi.user.service;
public class TokenReusedException extends RuntimeException {
  public TokenReusedException() { super("Refresh token reuse detected — all sessions revoked"); }
}
