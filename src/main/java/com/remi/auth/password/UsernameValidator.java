package com.remi.auth.password;

import com.remi.user.service.UsernamePolicyViolationException;
import java.util.regex.Pattern;

public final class UsernameValidator {
  private static final int MIN_LENGTH = 3;
  private static final int MAX_LENGTH = 20;
  private static final Pattern ALLOWED = Pattern.compile("^[a-zA-Z0-9_-]+$");
  private UsernameValidator() {}
  public static void validate(String username) {
    if (username == null || username.length() < MIN_LENGTH || username.length() > MAX_LENGTH) {
      throw new UsernamePolicyViolationException("Username-ul trebuie să aibă între " + MIN_LENGTH + " și " + MAX_LENGTH + " caractere.");
    }
    if (!ALLOWED.matcher(username).matches()) {
      throw new UsernamePolicyViolationException("Username-ul poate conține doar litere, cifre, underscore și liniuță.");
    }
  }
}
