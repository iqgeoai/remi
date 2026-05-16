package com.remi.engine.rules;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.MeldBuilder;
import org.junit.jupiter.api.Test;
import static com.remi.engine.testdata.Pieces.*;
import static org.assertj.core.api.Assertions.assertThat;

class MeldValidatorSuiteTest {
  @Test void validSuiteOf3Consecutive() {
    Meld m = MeldBuilder.suite(p(5, Color.RED), p(6, Color.RED), p(7, Color.RED)).build();
    assertThat(MeldValidator.isValid(m)).isTrue();
  }
  @Test void invalidSuiteMixedColors() {
    Meld m = MeldBuilder.suite(p(5, Color.RED), p(6, Color.BLUE), p(7, Color.RED)).build();
    assertThat(MeldValidator.isValid(m)).isFalse();
  }
  @Test void invalidSuiteNonConsecutive() {
    Meld m = MeldBuilder.suite(p(5, Color.RED), p(7, Color.RED), p(8, Color.RED)).build();
    assertThat(MeldValidator.isValid(m)).isFalse();
  }
  @Test void validSuiteWithJokerInMiddle() {
    Meld m = MeldBuilder.suite(p(5, Color.RED), joker(), p(7, Color.RED)).build();
    assertThat(MeldValidator.isValid(m)).isTrue();
  }
  @Test void validSuiteWithJokerAtStart() {
    Meld m = MeldBuilder.suite(joker(), p(6, Color.RED), p(7, Color.RED)).build();
    assertThat(MeldValidator.isValid(m)).isTrue();
  }
  @Test void validSuiteWrap12_13_1() {
    Meld m = MeldBuilder.suite(p(12, Color.RED), p(13, Color.RED), p(1, Color.RED)).build();
    assertThat(MeldValidator.isValid(m)).isTrue();
  }
  @Test void invalidSuiteOverflowPast14() {
    Meld m = MeldBuilder.suite(p(12, Color.RED), p(13, Color.RED), p(1, Color.RED), p(2, Color.RED)).build();
    assertThat(MeldValidator.isValid(m)).isFalse();
  }
  @Test void invalidSuiteTooShort() {
    Meld m = MeldBuilder.suite(p(5, Color.RED), p(6, Color.RED)).build();
    assertThat(MeldValidator.isValid(m)).isFalse();
  }
}
