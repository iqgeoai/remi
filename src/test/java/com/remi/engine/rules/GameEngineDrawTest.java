package com.remi.engine.rules;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.GameStateBuilder;
import org.junit.jupiter.api.Test;
import static com.remi.engine.testdata.Pieces.*;
import static org.assertj.core.api.Assertions.assertThat;

class GameEngineDrawTest {
  @Test void drawFromStockTransfersTopPieceAndMovesToAction() {
    Piece top = p(8, Color.RED);
    GameState s = GameStateBuilder.aGame().withPlayers("A", "B")
        .phase(Phase.DRAW).current(0).stock(p(3, Color.BLUE), top).build();
    ActionResult r = GameEngine.apply(s, new Action.DrawFromStock(0));
    assertThat(r).isInstanceOf(ActionResult.Accepted.class);
    GameState ns = ((ActionResult.Accepted) r).newState();
    assertThat(ns.players().get(0).hand()).contains(top);
    assertThat(ns.stock()).doesNotContain(top);
    assertThat(ns.phase()).isEqualTo(Phase.ACTION);
    assertThat(ns.drewFrom()).isEqualTo(DrawSource.STOCK);
  }
  @Test void drawRejectedIfPhaseIsAction() {
    GameState s = GameStateBuilder.aGame().withPlayers("A", "B")
        .phase(Phase.ACTION).current(0).stock(p(3, Color.BLUE)).build();
    ActionResult r = GameEngine.apply(s, new Action.DrawFromStock(0));
    assertThat(((ActionResult.Rejected) r).code()).isEqualTo(RejectReason.WRONG_PHASE);
  }
  @Test void drawRejectedIfStockEmpty() {
    GameState s = GameStateBuilder.aGame().withPlayers("A", "B")
        .phase(Phase.DRAW).current(0).build();
    ActionResult r = GameEngine.apply(s, new Action.DrawFromStock(0));
    assertThat(((ActionResult.Rejected) r).code()).isEqualTo(RejectReason.STOCK_EMPTY);
  }
}
