package com.remi.auth.password;

import java.util.Locale;
import java.util.Objects;

public final class EmailNormalizer {
  private EmailNormalizer() {}
  public static String normalize(String email) {
    Objects.requireNonNull(email, "email");
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
