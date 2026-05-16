package com.remi.engine.rules;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.MeldBuilder;
import org.junit.jupiter.api.Test;
import static com.remi.engine.testdata.Pieces.*;
import static org.assertj.core.api.Assertions.assertThat;

class ScoringPieceValueTest {
  @Test void finalValueJokerIs50()       { assertThat(Scoring.finalPieceValue(joker())).isEqualTo(50); }
  @Test void finalValue1Is25()           { assertThat(Scoring.finalPieceValue(p(1, Color.RED))).isEqualTo(25); }
  @Test void finalValue5Is5()            { assertThat(Scoring.finalPieceValue(p(5, Color.RED))).isEqualTo(5); }
  @Test void finalValue9Is5()            { assertThat(Scoring.finalPieceValue(p(9, Color.RED))).isEqualTo(5); }
  @Test void finalValue10Is10()          { assertThat(Scoring.finalPieceValue(p(10, Color.RED))).isEqualTo(10); }
  @Test void finalValue13Is10()          { assertThat(Scoring.finalPieceValue(p(13, Color.RED))).isEqualTo(10); }

  @Test void firstMeldValue1InGroupIs25() {
    Piece one = p(1, Color.RED);
    Meld m = MeldBuilder.group(one, p(1, Color.BLUE), p(1, Color.BLACK)).build();
    assertThat(Scoring.firstMeldPieceValue(one, m)).isEqualTo(25);
  }
  @Test void firstMeldValue1AtStartOfSuiteIs5() {
    Piece one = p(1, Color.RED);
    Meld m = MeldBuilder.suite(one, p(2, Color.RED), p(3, Color.RED)).build();
    assertThat(Scoring.firstMeldPieceValue(one, m)).isEqualTo(5);
  }
  @Test void firstMeldValue1AtEndOfSuiteIs10() {
    Piece one = p(1, Color.RED);
    Meld m = MeldBuilder.suite(p(12, Color.RED), p(13, Color.RED), one).build();
    assertThat(Scoring.firstMeldPieceValue(one, m)).isEqualTo(10);
  }
  @Test void firstMeldJokerInGroupOf7sIs5() {
    Piece j = joker();
    Meld m = MeldBuilder.group(p(7, Color.RED), p(7, Color.BLUE), j).build();
    assertThat(Scoring.firstMeldPieceValue(j, m)).isEqualTo(5);
  }
  @Test void firstMeldJokerInGroupOf10sIs10() {
    Piece j = joker();
    Meld m = MeldBuilder.group(p(10, Color.RED), p(10, Color.BLUE), j).build();
    assertThat(Scoring.firstMeldPieceValue(j, m)).isEqualTo(10);
  }
  @Test void firstMeldJokerInGroupOf1sIs25() {
    Piece j = joker();
    Meld m = MeldBuilder.group(p(1, Color.RED), p(1, Color.BLUE), j).build();
    assertThat(Scoring.firstMeldPieceValue(j, m)).isEqualTo(25);
  }
}
