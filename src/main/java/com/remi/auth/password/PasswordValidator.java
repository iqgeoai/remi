package com.remi.auth.password;

import com.remi.user.service.PasswordPolicyViolationException;

public final class PasswordValidator {
  private static final int MIN_LENGTH = 10;
  private static final int MAX_LENGTH = 200;
  private PasswordValidator() {}
  public static void validate(String password) {
    if (password == null || password.length() < MIN_LENGTH) {
      throw new PasswordPolicyViolationException("Parola trebuie să aibă cel puțin " + MIN_LENGTH + " caractere.");
    }
    if (password.length() > MAX_LENGTH) {
      throw new PasswordPolicyViolationException("Parola este prea lungă (max " + MAX_LENGTH + ").");
    }
  }
}
