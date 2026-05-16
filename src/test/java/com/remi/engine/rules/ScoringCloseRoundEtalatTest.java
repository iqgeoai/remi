package com.remi.engine.rules;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.remi.engine.testdata.Pieces.*;
import static org.assertj.core.api.Assertions.assertThat;

class ScoringCloseRoundEtalatTest {
  @Test void notEtalatPenaltyMinus100() {
    GameState s = GameStateBuilder.aGame().withPlayers("A", "B")
        .hand(0, p(5, Color.RED), p(6, Color.RED))
        .hand(1, p(7, Color.BLUE)).etalat(1)
        .build();
    List<RoundResult> r = Scoring.closeRound(s, 1, null);
    assertThat(r.get(0).base()).isEqualTo(-100);
  }
  @Test void closingBonusPlus50() {
    GameState s = GameStateBuilder.aGame().withPlayers("A", "B")
        .hand(0, p(5, Color.RED)).etalat(0)
        .hand(1, p(7, Color.BLUE)).etalat(1)
        .melds(MeldBuilder.group(p(7,Color.RED), p(7,Color.BLUE), p(7,Color.BLACK)).owner(0).build())
        .build();
    List<RoundResult> r = Scoring.closeRound(s, 0, null);
    // closer (0): meld 15p - hand 5p + 50 bonus = 60
    assertThat(r.get(0).base()).isEqualTo(60);
  }
  @Test void closingWithJokerDoublesCloserOnly() {
    GameState s = GameStateBuilder.aGame().withPlayers("A", "B")
        .hand(0).etalat(0)
        .hand(1, p(7, Color.BLUE)).etalat(1)
        .melds(MeldBuilder.group(p(7,Color.RED), p(7,Color.BLUE), p(7,Color.BLACK)).owner(0).build())
        .build();
    Piece closingJoker = joker();
    List<RoundResult> r = Scoring.closeRound(s, 0, closingJoker);
    // closer: meld 15 - hand 0 + 50 = 65, doubled = 130
    assertThat(r.get(0).base()).isEqualTo(130);
  }
  @Test void jocDubluDoublesAll() {
    GameState s = GameStateBuilder.aGame().withPlayers("A", "B")
        .hand(0).etalat(0)
        .hand(1, p(7, Color.BLUE)).etalat(1)
        .melds(MeldBuilder.group(p(7,Color.RED), p(7,Color.BLUE), p(7,Color.BLACK)).owner(0).build())
        .doubleGame(true).build();
    List<RoundResult> r = Scoring.closeRound(s, 0, null);
    assertThat(r.get(0).base()).isEqualTo((15 + 50) * 2);   // 130
    assertThat(r.get(1).base()).isEqualTo((-5) * 2);        // -10
  }
}
