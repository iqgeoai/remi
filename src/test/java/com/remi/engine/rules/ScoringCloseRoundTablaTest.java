package com.remi.engine.rules;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.remi.engine.testdata.Pieces.*;
import static org.assertj.core.api.Assertions.assertThat;

class ScoringCloseRoundTablaTest {
  @Test void tablaCloserGets250PerOther() {
    GameState s = GameStateBuilder.aGame().withPlayers("A","B","C").mode(Mode.TABLA).build();
    List<RoundResult> r = Scoring.closeRound(s, 0, null);
    assertThat(r.get(0).base()).isEqualTo(500);
    assertThat(r.get(1).base()).isEqualTo(-250);
    assertThat(r.get(2).base()).isEqualTo(-250);
  }
  @Test void tablaClosedWithJokerIs500() {
    GameState s = GameStateBuilder.aGame().withPlayers("A","B").mode(Mode.TABLA).build();
    List<RoundResult> r = Scoring.closeRound(s, 0, joker());
    assertThat(r.get(0).base()).isEqualTo(500);
    assertThat(r.get(1).base()).isEqualTo(-500);
  }
  @Test void tablaJocDubluDoublesAll() {
    GameState s = GameStateBuilder.aGame().withPlayers("A","B").mode(Mode.TABLA).doubleGame(true).build();
    List<RoundResult> r = Scoring.closeRound(s, 0, null);
    assertThat(r.get(0).base()).isEqualTo(500);   // 250 * 2
    assertThat(r.get(1).base()).isEqualTo(-500);
  }
}
