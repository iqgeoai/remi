package com.remi.engine.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PieceTest {
  @Test void sameValueIgnoresId() {
    Piece a = new Piece(1, 5, Color.RED, false);
    Piece b = new Piece(99, 5, Color.RED, false);
    assertThat(Piece.sameValue(a, b)).isTrue();
  }
  @Test void sameValueFalseOnDifferentNum() {
    assertThat(Piece.sameValue(new Piece(1, 5, Color.RED, false), new Piece(2, 6, Color.RED, false))).isFalse();
  }
  @Test void twoJokersAreSameValue() {
    assertThat(Piece.sameValue(new Piece(1, 0, Color.JOKER, true), new Piece(2, 0, Color.JOKER, true))).isTrue();
  }
  @Test void jokerVsNonJokerNotSame() {
    assertThat(Piece.sameValue(new Piece(1, 0, Color.JOKER, true), new Piece(2, 5, Color.RED, false))).isFalse();
  }
}
