package com.remi.engine.rules;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.remi.engine.testdata.Pieces.*;
import static org.assertj.core.api.Assertions.assertThat;

class GameEngineLayoffTest {
  @Test void rejectsIfNotEtalat() {
    Piece p4 = p(4, Color.RED);
    Meld existing = MeldBuilder.suite(p(5, Color.RED), p(6, Color.RED), p(7, Color.RED)).owner(1).build();
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.ACTION).current(0).hand(0, p4).melds(existing).build();
    ActionResult r = GameEngine.apply(s, new Action.Layoff(0, List.of(new LayoffProposal(p4.id(), 0))));
    assertThat(((ActionResult.Rejected) r).code()).isEqualTo(RejectReason.NOT_ETALAT);
  }
  @Test void extendsSuiteAtLowEnd() {
    Piece p4 = p(4, Color.RED);
    Meld existing = MeldBuilder.suite(p(5, Color.RED), p(6, Color.RED), p(7, Color.RED)).owner(1).build();
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.ACTION).current(0).etalat(0).hand(0, p4).melds(existing).build();
    ActionResult r = GameEngine.apply(s, new Action.Layoff(0, List.of(new LayoffProposal(p4.id(), 0))));
    assertThat(r).isInstanceOf(ActionResult.Accepted.class);
    GameState ns = ((ActionResult.Accepted) r).newState();
    assertThat(ns.melds().get(0).pieces()).hasSize(4).contains(p4);
    assertThat(ns.melds().get(0).placedBy().get(p4.id())).isEqualTo(0);
    assertThat(ns.players().get(0).hand()).isEmpty();
  }
  @Test void rejectsIfPieceDoesntFit() {
    Piece p20 = p(13, Color.BLUE);  // wrong color
    Meld existing = MeldBuilder.suite(p(5, Color.RED), p(6, Color.RED), p(7, Color.RED)).owner(1).build();
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.ACTION).current(0).etalat(0).hand(0, p20).melds(existing).build();
    ActionResult r = GameEngine.apply(s, new Action.Layoff(0, List.of(new LayoffProposal(p20.id(), 0))));
    assertThat(((ActionResult.Rejected) r).code()).isEqualTo(RejectReason.INVALID_LAYOFF);
  }
}
