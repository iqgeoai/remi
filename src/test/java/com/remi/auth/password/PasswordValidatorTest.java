package com.remi.auth.password;

import com.remi.user.service.PasswordPolicyViolationException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PasswordValidatorTest {
  @Test void exactly10CharsIsValid() {
    assertThatNoException().isThrownBy(() -> PasswordValidator.validate("1234567890"));
  }
  @Test void nineCharsRejected() {
    assertThatThrownBy(() -> PasswordValidator.validate("123456789"))
        .isInstanceOf(PasswordPolicyViolationException.class);
  }
  @Test void emptyRejected() {
    assertThatThrownBy(() -> PasswordValidator.validate(""))
        .isInstanceOf(PasswordPolicyViolationException.class);
  }
  @Test void nullRejected() {
    assertThatThrownBy(() -> PasswordValidator.validate(null))
        .isInstanceOf(PasswordPolicyViolationException.class);
  }
  @Test void longPasswordValid() {
    assertThatNoException().isThrownBy(() -> PasswordValidator.validate("a".repeat(200)));
  }
}
