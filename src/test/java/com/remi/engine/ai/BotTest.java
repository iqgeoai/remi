package com.remi.engine.ai;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.GameStateBuilder;
import com.remi.engine.testdata.MeldBuilder;
import org.junit.jupiter.api.Test;
import static com.remi.engine.testdata.Pieces.*;
import static org.assertj.core.api.Assertions.assertThat;

class BotTest {
  @Test void drawsFromStockInDrawPhase() {
    GameState s = GameStateBuilder.aGame().withPlayers("A", "Bot").current(1)
        .phase(Phase.DRAW).stock(p(2, Color.RED)).build();
    Action a = Bot.decide(s, 1);
    assertThat(a).isInstanceOf(Action.DrawFromStock.class);
  }
  @Test void discardsHighestValueNonJokerInAction() {
    GameState s = GameStateBuilder.aGame().withPlayers("A", "Bot").current(1)
        .phase(Phase.ACTION)
        .hand(1, p(2, Color.RED), p(13, Color.BLUE), p(1, Color.YELLOW)).build();
    Action a = Bot.decide(s, 1);
    assertThat(a).isInstanceOf(Action.Discard.class);
    int pid = ((Action.Discard) a).pieceId();
    // 1=25 is highest non-joker
    assertThat(s.players().get(1).hand().stream().filter(p -> p.id() == pid).findFirst().get().num()).isEqualTo(1);
  }
  @Test void etalatsWhenFirstMeldSetExists() {
    Piece p10 = p(10, Color.RED), p11 = p(11, Color.RED), p12 = p(12, Color.RED), p13 = p(13, Color.RED);
    Piece p1r = p(1, Color.RED), p1b = p(1, Color.BLUE), p1k = p(1, Color.BLACK);
    GameState s = GameStateBuilder.aGame().withPlayers("A", "Bot").current(1)
        .phase(Phase.ACTION)
        .hand(1, p10, p11, p12, p13, p1r, p1b, p1k).build();
    Action a = Bot.decide(s, 1);
    assertThat(a).isInstanceOf(Action.Etalat.class);
  }

  @Test void tablaModeClosesWhenHandFits() {
    Piece p5r = p(5, Color.RED), p6r = p(6, Color.RED), p7r = p(7, Color.RED);
    GameState s = GameStateBuilder.aGame().withPlayers("A", "Bot").current(1)
        .phase(Phase.ACTION).mode(Mode.TABLA)
        .hand(1, p5r, p6r, p7r).build();
    Action a = Bot.decide(s, 1);
    // Full coverage by one meld, zero uncovered → returns discard of hand[0]
    assertThat(a).isInstanceOf(Action.Discard.class);
  }

  @Test void tablaModeClosesWithOneLeftover() {
    Piece p5r = p(5, Color.RED), p6r = p(6, Color.RED), p7r = p(7, Color.RED), pX = p(13, Color.BLUE);
    GameState s = GameStateBuilder.aGame().withPlayers("A", "Bot").current(1)
        .phase(Phase.ACTION).mode(Mode.TABLA)
        .hand(1, p5r, p6r, p7r, pX).build();
    Action a = Bot.decide(s, 1);
    // One meld covers 3 pieces, one leftover → discard the leftover
    assertThat(a).isInstanceOf(Action.Discard.class);
    assertThat(((Action.Discard) a).pieceId()).isEqualTo(pX.id());
  }

  @Test void hasEtalatPlaysAdditionalMeld() {
    Piece p7r = p(7, Color.RED), p7b = p(7, Color.BLUE), p7k = p(7, Color.BLACK);
    GameState s = GameStateBuilder.aGame().withPlayers("A", "Bot").current(1)
        .phase(Phase.ACTION).etalat(1)
        .hand(1, p7r, p7b, p7k, p(13, Color.YELLOW)).build();
    Action a = Bot.decide(s, 1);
    assertThat(a).isInstanceOf(Action.Etalat.class);
  }

  @Test void hasEtalatLaysOffOntoExistingMeld() {
    Piece p4r = p(4, Color.RED);
    Meld existing = MeldBuilder.suite(p(5, Color.RED), p(6, Color.RED), p(7, Color.RED)).owner(0).build();
    GameState s = GameStateBuilder.aGame().withPlayers("A", "Bot").current(1)
        .phase(Phase.ACTION).etalat(1)
        .hand(1, p4r, p(13, Color.YELLOW)).melds(existing).build();
    Action a = Bot.decide(s, 1);
    assertThat(a).isInstanceOf(Action.Layoff.class);
  }

  @Test void chooseDiscardFallsBackToJokerWhenAllJokers() {
    Piece j = joker();
    GameState s = GameStateBuilder.aGame().withPlayers("A", "Bot").current(1)
        .phase(Phase.ACTION).hand(1, j).build();
    Action a = Bot.decide(s, 1);
    assertThat(a).isInstanceOf(Action.Discard.class);
    assertThat(((Action.Discard) a).pieceId()).isEqualTo(j.id());
  }
}
