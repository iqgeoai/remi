package com.remi.lobby.service;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;

@Component
public class JoinCodeGenerator {
  private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  private static final int CODE_LENGTH = 8;
  private final SecureRandom rng = new SecureRandom();

  public String generate() {
    StringBuilder sb = new StringBuilder(CODE_LENGTH);
    for (int i = 0; i < CODE_LENGTH; i++) {
      sb.append(ALPHABET.charAt(rng.nextInt(ALPHABET.length())));
    }
    return sb.toString();
  }
}
