package com.remi.engine.ai;

import com.remi.engine.domain.*;
import com.remi.engine.rules.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BotGoldenPlaythroughTest {
  @Test void fullGameAllBotsSeed42_terminates() {
    GameState s = Dealer.deal(3, Mode.ETALAT, Difficulty.MED, 42L);
    // Mark player 0 as bot too for full AI playthrough
    Player human = s.players().get(0);
    Player asBot = new Player(human.name(), true, human.hand(), false, human.calledAtu(), false, null);
    var newPlayers = new java.util.ArrayList<>(s.players()); newPlayers.set(0, asBot);
    s = new GameState(s.id(), newPlayers, s.stock(), s.discard(), s.atu(), s.melds(),
        s.current(), s.phase(), s.drewFrom(), s.turnTaken(), s.round(), s.mode(),
        s.difficulty(), s.doubleGame(), s.closed(), s.totals(), s.seed());

    int safety = 1000;
    while (!s.closed() && safety-- > 0) {
      Action a = Bot.decide(s, s.current());
      ActionResult r = GameEngine.apply(s, a);
      if (r instanceof ActionResult.Rejected rej) {
        throw new AssertionError("Bot proposed rejected action: " + rej.code() + " — " + rej.message());
      }
      s = ((ActionResult.Accepted) r).newState();
    }
    assertThat(s.closed()).as("game terminated").isTrue();
    assertThat(safety).as("did not hit safety limit").isPositive();
    assertThat(s.totals()).containsExactly(/* p0 */ -100, /* p1 */ -100, /* p2 */ 205);
  }
}
