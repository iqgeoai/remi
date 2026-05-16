package com.remi.engine.rules;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.GameStateBuilder;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.remi.engine.testdata.Pieces.*;
import static org.assertj.core.api.Assertions.assertThat;

class GameEngineEtalatTest {
  @Test void firstMeldRequires45Points() {
    Piece p5 = p(5, Color.RED), p6 = p(6, Color.RED), p7 = p(7, Color.RED);
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.ACTION).current(0).hand(0, p5, p6, p7).build();
    ActionResult r = GameEngine.apply(s,
        new Action.Etalat(0, List.of(new MeldProposal(MeldType.SUITE, List.of(p5.id(), p6.id(), p7.id())))));
    // 5+5+5 = 15 < 45
    assertThat(((ActionResult.Rejected) r).code()).isEqualTo(RejectReason.FIRST_MELD_TOO_FEW_POINTS);
  }
  @Test void firstMeldRequiresSuiteOr1sGroup() {
    Piece p7r = p(7, Color.RED), p7b = p(7, Color.BLUE), p7k = p(7, Color.BLACK), p7y = p(7, Color.YELLOW);
    Piece p10r = p(10, Color.RED), p10b = p(10, Color.BLUE), p10k = p(10, Color.BLACK), p10y = p(10, Color.YELLOW);
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.ACTION).current(0)
        .hand(0, p7r, p7b, p7k, p7y, p10r, p10b, p10k, p10y).build();
    // 2 groups: 5*4 + 10*4 = 60 ≥ 45 but no suite and no 1s
    ActionResult r = GameEngine.apply(s, new Action.Etalat(0, List.of(
        new MeldProposal(MeldType.GROUP, List.of(p7r.id(), p7b.id(), p7k.id(), p7y.id())),
        new MeldProposal(MeldType.GROUP, List.of(p10r.id(), p10b.id(), p10k.id(), p10y.id())))));
    assertThat(((ActionResult.Rejected) r).code()).isEqualTo(RejectReason.FIRST_MELD_NEEDS_SUITE_OR_1S);
  }
  @Test void firstMeldAcceptsGroupOf1sAlone() {
    Piece p1r = p(1, Color.RED), p1b = p(1, Color.BLUE), p1k = p(1, Color.BLACK);
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.ACTION).current(0).hand(0, p1r, p1b, p1k).build();
    ActionResult r = GameEngine.apply(s, new Action.Etalat(0, List.of(
        new MeldProposal(MeldType.GROUP, List.of(p1r.id(), p1b.id(), p1k.id())))));
    // 25*3 = 75 ≥ 45, has 1s group
    assertThat(r).isInstanceOf(ActionResult.Accepted.class);
    GameState ns = ((ActionResult.Accepted) r).newState();
    assertThat(ns.players().get(0).hasEtalat()).isTrue();
    assertThat(ns.melds()).hasSize(1);
  }
  @Test void rejectsInvalidMeld() {
    Piece p5 = p(5, Color.RED), p6 = p(7, Color.RED), p7 = p(8, Color.RED);  // 5-7-8 non-consecutive
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.ACTION).current(0).etalat(0).hand(0, p5, p6, p7).build();
    ActionResult r = GameEngine.apply(s, new Action.Etalat(0, List.of(
        new MeldProposal(MeldType.SUITE, List.of(p5.id(), p6.id(), p7.id())))));
    assertThat(((ActionResult.Rejected) r).code()).isEqualTo(RejectReason.INVALID_MELD);
  }
  @Test void rejectsPiecesNotInHand() {
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.ACTION).current(0).etalat(0).hand(0, p(5, Color.RED)).build();
    ActionResult r = GameEngine.apply(s, new Action.Etalat(0, List.of(
        new MeldProposal(MeldType.GROUP, List.of(999, 998, 997)))));
    assertThat(((ActionResult.Rejected) r).code()).isEqualTo(RejectReason.PIECE_NOT_IN_HAND);
  }
  @Test void clearsMustUseIfPlayed() {
    Piece p10r = p(10, Color.RED), p10b = p(10, Color.BLUE), p10k = p(10, Color.BLACK);
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.ACTION).current(0).etalat(0)
        .hand(0, p10r, p10b, p10k)
        .mustUse(0, p10r.id()).build();
    ActionResult r = GameEngine.apply(s, new Action.Etalat(0, List.of(
        new MeldProposal(MeldType.GROUP, List.of(p10r.id(), p10b.id(), p10k.id())))));
    assertThat(r).isInstanceOf(ActionResult.Accepted.class);
    GameState ns = ((ActionResult.Accepted) r).newState();
    assertThat(ns.players().get(0).mustUsePieceId()).isNull();
  }
}
