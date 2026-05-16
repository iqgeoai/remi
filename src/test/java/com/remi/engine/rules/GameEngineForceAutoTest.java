package com.remi.engine.rules;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.GameStateBuilder;
import org.junit.jupiter.api.Test;
import static com.remi.engine.testdata.Pieces.*;
import static org.assertj.core.api.Assertions.assertThat;

class GameEngineForceAutoTest {
  @Test void forceAutoDrawsFromStockWhenPhaseDraw() {
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.DRAW).current(0).hand(0, p(5, Color.RED))
        .stock(p(2, Color.BLACK), p(3, Color.BLUE)).build();
    ActionResult r = GameEngine.apply(s, new Action.ForceAutoAction(0));
    GameState ns = ((ActionResult.Accepted) r).newState();
    assertThat(ns.phase()).isEqualTo(Phase.ACTION);
    assertThat(ns.players().get(0).hand()).hasSize(2);
  }
  @Test void forceAutoDiscardsHighestNonJokerNonMustUse() {
    Piece p5 = p(5, Color.RED), p13 = p(13, Color.BLUE), p1 = p(1, Color.RED);
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.ACTION).current(0).hand(0, p5, p13, p1)
        .hand(1, p(7, Color.YELLOW)).stock(p(2, Color.BLACK)).build();
    ActionResult r = GameEngine.apply(s, new Action.ForceAutoAction(0));
    GameState ns = ((ActionResult.Accepted) r).newState();
    // p1=25, p13=10, p5=5 → p1 has highest value
    assertThat(ns.discard()).contains(p1);
  }
  @Test void forceAutoDiscardSkipsMustUse() {
    Piece p5 = p(5, Color.RED), p13 = p(13, Color.BLUE);
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.ACTION).current(0).hand(0, p5, p13).mustUse(0, p13.id())
        .hand(1, p(7, Color.YELLOW)).stock(p(2, Color.BLACK)).build();
    ActionResult r = GameEngine.apply(s, new Action.ForceAutoAction(0));
    GameState ns = ((ActionResult.Accepted) r).newState();
    assertThat(ns.discard()).contains(p5);
  }
}
