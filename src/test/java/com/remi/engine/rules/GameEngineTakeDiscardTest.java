package com.remi.engine.rules;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.GameStateBuilder;
import org.junit.jupiter.api.Test;
import static com.remi.engine.testdata.Pieces.*;
import static org.assertj.core.api.Assertions.assertThat;

class GameEngineTakeDiscardTest {
  @Test void takesTopDiscardSetsMustUse() {
    Piece top = p(7, Color.BLUE);
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.DRAW).current(0)
        .hand(0, p(5, Color.RED))
        .discard(p(3, Color.BLACK), top).build();
    ActionResult r = GameEngine.apply(s, new Action.TakeDiscard(0, 1));
    assertThat(r).isInstanceOf(ActionResult.Accepted.class);
    GameState ns = ((ActionResult.Accepted) r).newState();
    assertThat(ns.players().get(0).hand()).contains(top);
    assertThat(ns.players().get(0).mustUsePieceId()).isEqualTo(top.id());
    assertThat(ns.discard()).doesNotContain(top);
    assertThat(ns.phase()).isEqualTo(Phase.ACTION);
    assertThat(ns.drewFrom()).isEqualTo(DrawSource.DISCARD);
  }
  @Test void rejectsTakingOpeningPiece() {
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.DRAW).current(0).hand(0, p(5, Color.RED))
        .discard(p(3, Color.BLACK), p(7, Color.BLUE)).build();
    ActionResult r = GameEngine.apply(s, new Action.TakeDiscard(0, 0));
    assertThat(((ActionResult.Rejected) r).code()).isEqualTo(RejectReason.CANNOT_TAKE_OPENING_PIECE);
  }
  @Test void rejectsBreakLineWithoutEtalat() {
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.DRAW).current(0)
        .hand(0, p(5, Color.RED), p(6, Color.RED), p(7, Color.RED), p(8, Color.RED))
        .discard(p(2, Color.BLACK), p(3, Color.BLACK), p(4, Color.BLACK)).build();
    ActionResult r = GameEngine.apply(s, new Action.TakeDiscard(0, 1));  // non-top
    assertThat(((ActionResult.Rejected) r).code()).isEqualTo(RejectReason.BREAK_REQUIRES_ETALAT);
  }
  @Test void allowsBreakLineForEtalatWith4Plus() {
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.DRAW).current(0).etalat(0)
        .hand(0, p(5, Color.RED), p(6, Color.RED), p(7, Color.RED), p(8, Color.RED))
        .discard(p(2, Color.BLACK), p(3, Color.BLACK), p(4, Color.BLACK)).build();
    ActionResult r = GameEngine.apply(s, new Action.TakeDiscard(0, 1));
    assertThat(r).isInstanceOf(ActionResult.Accepted.class);
    GameState ns = ((ActionResult.Accepted) r).newState();
    assertThat(ns.players().get(0).hand()).hasSize(6); // 4 + 2 taken
  }
  @Test void rejectsBreakLineWithHandSize3() {
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.DRAW).current(0).etalat(0)
        .hand(0, p(5, Color.RED), p(6, Color.RED), p(7, Color.RED))
        .discard(p(2, Color.BLACK), p(3, Color.BLACK), p(4, Color.BLACK)).build();
    ActionResult r = GameEngine.apply(s, new Action.TakeDiscard(0, 1));
    assertThat(((ActionResult.Rejected) r).code()).isEqualTo(RejectReason.CANNOT_BREAK_LINE);
  }
  @Test void rejectsIfDiscardEmpty() {
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.DRAW).current(0).hand(0, p(5, Color.RED)).build();
    ActionResult r = GameEngine.apply(s, new Action.TakeDiscard(0, 0));
    assertThat(((ActionResult.Rejected) r).code()).isEqualTo(RejectReason.DISCARD_EMPTY);
  }
}
