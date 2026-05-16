package com.remi.engine.rules;

import com.remi.engine.domain.*;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class DealerTest {
  @Test void deckHas106Pieces() {
    GameState s = Dealer.deal(2, Mode.ETALAT, Difficulty.MED, 1L);
    int total = s.players().stream().mapToInt(p -> p.hand().size()).sum()
        + s.stock().size() + 1 /* atu */;
    assertThat(total).isEqualTo(106);
  }
  @Test void firstPlayerHas15Others14() {
    GameState s = Dealer.deal(3, Mode.ETALAT, Difficulty.MED, 1L);
    assertThat(s.players().get(0).hand()).hasSize(15);
    assertThat(s.players().get(1).hand()).hasSize(14);
    assertThat(s.players().get(2).hand()).hasSize(14);
  }
  @Test void allPieceIdsAreUnique() {
    GameState s = Dealer.deal(4, Mode.ETALAT, Difficulty.MED, 1L);
    Set<Integer> ids = new HashSet<>();
    s.players().forEach(p -> p.hand().forEach(piece -> ids.add(piece.id())));
    s.stock().forEach(piece -> ids.add(piece.id()));
    ids.add(s.atu().id());
    assertThat(ids).hasSize(106);
  }
  @Test void doubleGameWhenAtuIsJokerOr1() {
    boolean foundDouble = false, foundSingle = false;
    for (long seed = 1; seed < 500 && (!foundDouble || !foundSingle); seed++) {
      GameState s = Dealer.deal(2, Mode.ETALAT, Difficulty.MED, seed);
      boolean expected = s.atu().isJoker() || s.atu().num() == 1;
      assertThat(s.doubleGame()).isEqualTo(expected);
      if (expected) foundDouble = true; else foundSingle = true;
    }
    assertThat(foundDouble).isTrue();
    assertThat(foundSingle).isTrue();
  }
  @Test void seedIsDeterministic() {
    GameState a = Dealer.deal(3, Mode.ETALAT, Difficulty.MED, 42L);
    GameState b = Dealer.deal(3, Mode.ETALAT, Difficulty.MED, 42L);
    assertThat(a.atu()).isEqualTo(b.atu());
    assertThat(a.players().get(0).hand()).isEqualTo(b.players().get(0).hand());
  }
  @Test void calledAtuSetForPlayersHoldingAtuValue() {
    GameState s = Dealer.deal(3, Mode.ETALAT, Difficulty.MED, 42L);
    Piece atu = s.atu();
    for (int i = 0; i < s.players().size(); i++) {
      boolean holdsMatch = s.players().get(i).hand().stream().anyMatch(p -> Piece.sameValue(p, atu));
      assertThat(s.players().get(i).calledAtu()).isEqualTo(holdsMatch);
    }
  }
  @Test void firstPlayerNamedTu() {
    GameState s = Dealer.deal(3, Mode.ETALAT, Difficulty.MED, 42L);
    assertThat(s.players().get(0).name()).isEqualTo("Tu");
    assertThat(s.players().get(0).isBot()).isFalse();
  }
  @Test void otherPlayersAreBots() {
    GameState s = Dealer.deal(3, Mode.ETALAT, Difficulty.MED, 42L);
    assertThat(s.players().get(1).isBot()).isTrue();
    assertThat(s.players().get(2).isBot()).isTrue();
  }
}
