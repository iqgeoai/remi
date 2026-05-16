package com.remi.engine.rules;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.*;
import org.junit.jupiter.api.Test;
import static com.remi.engine.testdata.Pieces.*;
import static org.assertj.core.api.Assertions.assertThat;

class GameEngineDiscardTest {
  @Test void discardMovesPieceToDiscardPileAndAdvancesTurn() {
    Piece p5 = p(5, Color.RED);
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.ACTION).current(0).hand(0, p5)
        .hand(1, p(7, Color.BLUE)).stock(p(2, Color.BLACK)).build();
    ActionResult r = GameEngine.apply(s, new Action.Discard(0, p5.id()));
    GameState ns = ((ActionResult.Accepted) r).newState();
    assertThat(ns.players().get(0).hand()).doesNotContain(p5);
    assertThat(ns.discard()).contains(p5);
    assertThat(ns.current()).isEqualTo(1);
    assertThat(ns.phase()).isEqualTo(Phase.DRAW);
    assertThat(ns.turnTaken()).isEqualTo(1);
  }
  @Test void rejectsDiscardWithUnusedMustUse() {
    Piece p5 = p(5, Color.RED), p7 = p(7, Color.BLUE);
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.ACTION).current(0).hand(0, p5, p7)
        .mustUse(0, p5.id()).build();
    ActionResult r = GameEngine.apply(s, new Action.Discard(0, p7.id()));
    assertThat(((ActionResult.Rejected) r).code()).isEqualTo(RejectReason.MUST_USE_TAKEN_PIECE);
  }
  @Test void closesRoundWhenHandEmptyAndEtalat() {
    Piece p5 = p(5, Color.RED);
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.ACTION).current(0).etalat(0).hand(0, p5)
        .hand(1, p(7, Color.BLUE)).build();
    ActionResult r = GameEngine.apply(s, new Action.Discard(0, p5.id()));
    GameState ns = ((ActionResult.Accepted) r).newState();
    assertThat(ns.closed()).isTrue();
  }
  @Test void marksAnnouncedWhenHandSizeLeq3AfterDiscard() {
    Piece p5 = p(5, Color.RED);
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.ACTION).current(0).etalat(0)
        .hand(0, p5, p(6, Color.RED), p(7, Color.RED), p(8, Color.RED))
        .hand(1, p(9, Color.BLUE)).stock(p(2, Color.BLACK)).build();
    ActionResult r = GameEngine.apply(s, new Action.Discard(0, p5.id()));
    GameState ns = ((ActionResult.Accepted) r).newState();
    assertThat(ns.players().get(0).announced()).isTrue();
  }
  @Test void rejectsIfPieceNotInHand() {
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.ACTION).current(0).hand(0, p(5, Color.RED)).build();
    ActionResult r = GameEngine.apply(s, new Action.Discard(0, 9999));
    assertThat(((ActionResult.Rejected) r).code()).isEqualTo(RejectReason.PIECE_NOT_IN_HAND);
  }
}
