package com.remi.engine.ai;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.GameStateBuilder;
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
}
