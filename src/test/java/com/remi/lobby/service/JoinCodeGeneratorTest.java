package com.remi.lobby.service;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class JoinCodeGeneratorTest {
  private final JoinCodeGenerator gen = new JoinCodeGenerator();

  @Test void codeIs8CharsLong() {
    assertThat(gen.generate()).hasSize(8);
  }

  @Test void codeOnlyAlphanumericUppercase() {
    String code = gen.generate();
    assertThat(code).matches("^[A-Z0-9]+$");
  }

  @Test void thousandCodesHaveAtLeast999Distinct() {
    Set<String> codes = new HashSet<>();
    for (int i = 0; i < 1000; i++) codes.add(gen.generate());
    assertThat(codes.size()).isGreaterThanOrEqualTo(999);
  }
}
