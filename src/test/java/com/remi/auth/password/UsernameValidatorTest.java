package com.remi.auth.password;

import com.remi.user.service.UsernamePolicyViolationException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UsernameValidatorTest {
  @Test void threeCharsValid() {
    assertThatNoException().isThrownBy(() -> UsernameValidator.validate("abc"));
  }
  @Test void twentyCharsValid() {
    assertThatNoException().isThrownBy(() -> UsernameValidator.validate("a".repeat(20)));
  }
  @Test void twoCharsRejected() {
    assertThatThrownBy(() -> UsernameValidator.validate("ab")).isInstanceOf(UsernamePolicyViolationException.class);
  }
  @Test void twentyOneCharsRejected() {
    assertThatThrownBy(() -> UsernameValidator.validate("a".repeat(21))).isInstanceOf(UsernamePolicyViolationException.class);
  }
  @Test void alphanumericPlusUnderscoreDashValid() {
    assertThatNoException().isThrownBy(() -> UsernameValidator.validate("user_123-abc"));
  }
  @Test void spaceRejected() {
    assertThatThrownBy(() -> UsernameValidator.validate("user 123")).isInstanceOf(UsernamePolicyViolationException.class);
  }
  @Test void unicodeRejected() {
    assertThatThrownBy(() -> UsernameValidator.validate("usér")).isInstanceOf(UsernamePolicyViolationException.class);
  }
  @Test void nullRejected() {
    assertThatThrownBy(() -> UsernameValidator.validate(null)).isInstanceOf(UsernamePolicyViolationException.class);
  }
}
