package com.remi.engine.ai;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.MeldBuilder;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.remi.engine.testdata.Pieces.*;
import static org.assertj.core.api.Assertions.assertThat;

class MeldFinderTest {
  @Test void findsGroupOfSameNumDifferentColors() {
    List<Piece> hand = List.of(p(7, Color.RED), p(7, Color.BLUE), p(7, Color.BLACK), p(2, Color.RED));
    List<Meld> melds = MeldFinder.findAnyMelds(hand);
    assertThat(melds).hasSize(1);
    assertThat(melds.get(0).type()).isEqualTo(MeldType.GROUP);
  }
  @Test void findsSuiteOfConsecutiveSameColor() {
    List<Piece> hand = List.of(p(5, Color.RED), p(6, Color.RED), p(7, Color.RED), p(2, Color.BLUE));
    List<Meld> melds = MeldFinder.findAnyMelds(hand);
    assertThat(melds).hasSize(1);
    assertThat(melds.get(0).type()).isEqualTo(MeldType.SUITE);
  }
  @Test void findFirstMeldSetReturnsNullIfBelow45() {
    List<Piece> hand = List.of(p(2, Color.RED), p(3, Color.RED), p(4, Color.RED));
    assertThat(MeldFinder.findFirstMeldSet(hand)).isNull();
  }
  @Test void findFirstMeldSetReturnsValid() {
    List<Piece> hand = List.of(
        p(10, Color.RED), p(11, Color.RED), p(12, Color.RED), p(13, Color.RED),  // suite 10-13 = 40
        p(1, Color.RED), p(1, Color.BLUE), p(1, Color.BLACK)                       // group of 1s = 75
    );
    List<Meld> chosen = MeldFinder.findFirstMeldSet(hand);
    assertThat(chosen).isNotNull();
    assertThat(chosen).hasSizeGreaterThanOrEqualTo(1);
  }
  @Test void findLayoffsExtendsExistingSuite() {
    Piece p4 = p(4, Color.RED);
    Meld m = MeldBuilder.suite(p(5, Color.RED), p(6, Color.RED), p(7, Color.RED)).owner(1).build();
    List<LayoffProposal> los = MeldFinder.findLayoffs(List.of(p4), List.of(m));
    assertThat(los).hasSize(1);
    assertThat(los.get(0).pieceId()).isEqualTo(p4.id());
    assertThat(los.get(0).meldIdx()).isEqualTo(0);
  }
  @Test void findLayoffsRespectsCapacityFromPriorLayoffs() {
    // Group of 7s with 3 colors; hand has 4th color + joker.
    // Without the fix, findLayoffs would propose both; engine would reject the joker.
    Piece p7y = p(7, Color.YELLOW);
    Piece j = joker();
    Meld m = MeldBuilder.group(p(7, Color.RED), p(7, Color.BLUE), p(7, Color.BLACK)).owner(0).build();
    List<LayoffProposal> los = MeldFinder.findLayoffs(List.of(p7y, j), List.of(m));
    // Only ONE of them can be placed (the second would push to 5 pieces, exceeding group max).
    assertThat(los).hasSize(1);
  }
}
