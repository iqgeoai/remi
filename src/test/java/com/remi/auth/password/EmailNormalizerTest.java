package com.remi.auth.password;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EmailNormalizerTest {
  @Test void lowercasesEntireEmail() {
    assertThat(EmailNormalizer.normalize("Foo@Bar.COM")).isEqualTo("foo@bar.com");
  }
  @Test void trimsWhitespace() {
    assertThat(EmailNormalizer.normalize("  user@example.org  ")).isEqualTo("user@example.org");
  }
  @Test void preservesPlusAndDots() {
    assertThat(EmailNormalizer.normalize("u.s.er+tag@example.com")).isEqualTo("u.s.er+tag@example.com");
  }
  @Test void nullThrows() {
    org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> EmailNormalizer.normalize(null));
  }
}
