package com.remi.engine.rules;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.MeldBuilder;
import org.junit.jupiter.api.Test;
import static com.remi.engine.testdata.Pieces.*;
import static org.assertj.core.api.Assertions.assertThat;

class MeldValidatorGroupTest {
  @Test void validGroupOf3DifferentColors() {
    Meld m = MeldBuilder.group(p(7, Color.RED), p(7, Color.BLUE), p(7, Color.BLACK)).build();
    assertThat(MeldValidator.isValid(m)).isTrue();
  }
  @Test void validGroupOf4DifferentColors() {
    Meld m = MeldBuilder.group(p(7, Color.RED), p(7, Color.BLUE), p(7, Color.BLACK), p(7, Color.YELLOW)).build();
    assertThat(MeldValidator.isValid(m)).isTrue();
  }
  @Test void invalidGroupOf5() {
    Meld m = MeldBuilder.group(p(7, Color.RED), p(7, Color.BLUE), p(7, Color.BLACK), p(7, Color.YELLOW), p(7, Color.RED)).build();
    assertThat(MeldValidator.isValid(m)).isFalse();
  }
  @Test void invalidGroupSameColor() {
    Meld m = MeldBuilder.group(p(7, Color.RED), p(7, Color.RED), p(7, Color.BLUE)).build();
    assertThat(MeldValidator.isValid(m)).isFalse();
  }
  @Test void invalidGroupMixedNumbers() {
    Meld m = MeldBuilder.group(p(7, Color.RED), p(8, Color.BLUE), p(7, Color.BLACK)).build();
    assertThat(MeldValidator.isValid(m)).isFalse();
  }
  @Test void invalidGroupTooShort() {
    Meld m = MeldBuilder.group(p(7, Color.RED), p(7, Color.BLUE)).build();
    assertThat(MeldValidator.isValid(m)).isFalse();
  }
  @Test void validGroupWithOneJoker() {
    Meld m = MeldBuilder.group(p(7, Color.RED), p(7, Color.BLUE), joker()).build();
    assertThat(MeldValidator.isValid(m)).isTrue();
  }
  @Test void invalidGroupAllJokers() {
    Meld m = MeldBuilder.group(joker(), joker(), joker()).build();
    assertThat(MeldValidator.isValid(m)).isFalse();
  }
}
