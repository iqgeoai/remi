package com.remi.engine.rules;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.GameStateBuilder;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GameEngineDispatchTest {
  @Test void rejectsActionFromWrongPlayer() {
    GameState s = GameStateBuilder.aGame().withPlayers("A", "B").current(0).build();
    ActionResult r = GameEngine.apply(s, new Action.DrawFromStock(1));
    assertThat(r).isInstanceOf(ActionResult.Rejected.class);
    assertThat(((ActionResult.Rejected) r).code()).isEqualTo(RejectReason.NOT_YOUR_TURN);
  }
  @Test void rejectsActionWhenClosed() {
    GameState s = GameStateBuilder.aGame().withPlayers("A", "B").build();
    GameState closed = new GameState(s.id(), s.players(), s.stock(), s.discard(), s.atu(),
        s.melds(), s.current(), s.phase(), s.drewFrom(), s.turnTaken(), s.round(),
        s.mode(), s.difficulty(), s.doubleGame(), true, s.totals(), s.seed());
    ActionResult r = GameEngine.apply(closed, new Action.DrawFromStock(0));
    assertThat(r).isInstanceOf(ActionResult.Rejected.class);
    assertThat(((ActionResult.Rejected) r).code()).isEqualTo(RejectReason.GAME_CLOSED);
  }
}
