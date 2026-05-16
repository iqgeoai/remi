package com.remi.engine.property;

import com.remi.engine.ai.Bot;
import com.remi.engine.domain.*;
import com.remi.engine.rules.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import java.util.HashSet;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class EngineProperties {
  @Property
  void dealerAlwaysProducesValidState(
      @ForAll @IntRange(min = 2, max = 6) int numPlayers,
      @ForAll @LongRange(min = 1, max = 10000) long seed) {
    GameState s = Dealer.deal(numPlayers, Mode.ETALAT, Difficulty.MED, seed);
    assertThat(s.players()).hasSize(numPlayers);
    assertThat(s.players().get(0).hand()).hasSize(15);
    for (int i = 1; i < numPlayers; i++) assertThat(s.players().get(i).hand()).hasSize(14);

    Set<Integer> ids = new HashSet<>();
    s.players().forEach(p -> p.hand().forEach(piece -> ids.add(piece.id())));
    s.stock().forEach(piece -> ids.add(piece.id()));
    ids.add(s.atu().id());
    assertThat(ids).hasSize(106);
  }

  @Property
  void botActionsAreNeverRejectedInPureBotGame(
      @ForAll @LongRange(min = 1, max = 100) long seed) {
    GameState s = Dealer.deal(2, Mode.ETALAT, Difficulty.MED, seed);
    // Make all players bots
    var newPlayers = new java.util.ArrayList<Player>();
    for (Player p : s.players()) {
      newPlayers.add(new Player(p.name(), true, p.hand(), false, p.calledAtu(), false, null));
    }
    s = new GameState(s.id(), newPlayers, s.stock(), s.discard(), s.atu(), s.melds(),
        s.current(), s.phase(), s.drewFrom(), s.turnTaken(), s.round(), s.mode(),
        s.difficulty(), s.doubleGame(), s.closed(), s.totals(), s.seed());

    int safety = 2000;
    while (!s.closed() && safety-- > 0) {
      Action a = Bot.decide(s, s.current());
      ActionResult r = GameEngine.apply(s, a);
      if (r instanceof ActionResult.Rejected rej) {
        throw new AssertionError("Bot proposed rejected action with seed=" + seed
            + ", phase=" + s.phase() + ": " + rej.code());
      }
      s = ((ActionResult.Accepted) r).newState();
    }
    assertThat(s.closed()).as("seed=" + seed + " terminated").isTrue();
  }
}
