# Stage 1 — Game Engine Implementation Plan (Part 3: GameEngine actions + AI)

> **For agentic workers:** Continuation of part 2. Same TDD discipline: failing test → run → minimal impl → run → commit.

---

## Phase D continued — `GameEngine.apply` per action

### Task D2: Apply `DrawFromStock`

**Files:**
- Modify: `src/main/java/com/remi/engine/rules/GameEngine.java`
- Create: `src/test/java/com/remi/engine/rules/GameEngineDrawTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.remi.engine.rules;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.GameStateBuilder;
import org.junit.jupiter.api.Test;
import static com.remi.engine.testdata.Pieces.*;
import static org.assertj.core.api.Assertions.assertThat;

class GameEngineDrawTest {
  @Test void drawFromStockTransfersTopPieceAndMovesToAction() {
    Piece top = p(8, Color.RED);
    GameState s = GameStateBuilder.aGame().withPlayers("A", "B")
        .phase(Phase.DRAW).current(0).stock(p(3, Color.BLUE), top).build();
    ActionResult r = GameEngine.apply(s, new Action.DrawFromStock(0));
    assertThat(r).isInstanceOf(ActionResult.Accepted.class);
    GameState ns = ((ActionResult.Accepted) r).newState();
    assertThat(ns.players().get(0).hand()).contains(top);
    assertThat(ns.stock()).doesNotContain(top);
    assertThat(ns.phase()).isEqualTo(Phase.ACTION);
    assertThat(ns.drewFrom()).isEqualTo(DrawSource.STOCK);
  }
  @Test void drawRejectedIfPhaseIsAction() {
    GameState s = GameStateBuilder.aGame().withPlayers("A", "B")
        .phase(Phase.ACTION).current(0).stock(p(3, Color.BLUE)).build();
    ActionResult r = GameEngine.apply(s, new Action.DrawFromStock(0));
    assertThat(((ActionResult.Rejected) r).code()).isEqualTo(RejectReason.WRONG_PHASE);
  }
  @Test void drawRejectedIfStockEmpty() {
    GameState s = GameStateBuilder.aGame().withPlayers("A", "B")
        .phase(Phase.DRAW).current(0).build();
    ActionResult r = GameEngine.apply(s, new Action.DrawFromStock(0));
    assertThat(((ActionResult.Rejected) r).code()).isEqualTo(RejectReason.STOCK_EMPTY);
  }
}
```

- [ ] **Step 2: Run (FAIL)**

- [ ] **Step 3: Implement `applyDrawFromStock` in `GameEngine.java`** (replace stub)

```java
  private static ActionResult applyDrawFromStock(GameState s, Action.DrawFromStock a) {
    if (s.phase() != Phase.DRAW) return reject(RejectReason.WRONG_PHASE, "Nu poți trage acum.");
    if (s.stock().isEmpty()) return reject(RejectReason.STOCK_EMPTY, "Grămada este goală.");
    var newStock = new java.util.ArrayList<>(s.stock());
    Piece top = newStock.remove(newStock.size() - 1);
    Player p = s.players().get(s.current());
    var newHand = new java.util.ArrayList<>(p.hand()); newHand.add(top);
    var newPlayer = new Player(p.name(), p.isBot(), newHand, p.hasEtalat(), p.calledAtu(), p.announced(), p.mustUsePieceId());
    var newPlayers = new java.util.ArrayList<>(s.players()); newPlayers.set(s.current(), newPlayer);
    GameState ns = new GameState(s.id(), newPlayers, newStock, s.discard(), s.atu(), s.melds(),
        s.current(), Phase.ACTION, DrawSource.STOCK, s.turnTaken(), s.round(), s.mode(), s.difficulty(),
        s.doubleGame(), s.closed(), s.totals(), s.seed());
    return accept(ns, new DomainEvent.CardDrawn(s.current(), DrawSource.STOCK));
  }
```

- [ ] **Step 4: Run (PASS)** + **Step 5: Commit**

```bash
mvn -q test -Dtest=GameEngineDrawTest
git add src/main/java/com/remi/engine/rules/GameEngine.java src/test/java/com/remi/engine/rules/GameEngineDrawTest.java
git commit -m "feat(engine): GameEngine.applyDrawFromStock"
```

---

### Task D3: Apply `TakeDiscard` (with break-line rules)

**Files:**
- Modify: `src/main/java/com/remi/engine/rules/GameEngine.java`
- Create: `src/test/java/com/remi/engine/rules/GameEngineTakeDiscardTest.java`

JS reference: `takeDiscard()` in `remi.html:1269-1331`. Rules:
- Cannot take idx=0 if discard has >1 piece (opening piece reversed)
- hand.length ≤ 2 + not top → reject (CANNOT_BREAK_LINE)
- hand.length == 3 + not top → reject
- non-top: requires hasEtalat + hand.length ≥ 4
- Take piece at idx + everything above it; piece at idx becomes mustUsePieceId

- [ ] **Step 1: Write failing tests** (cover top, non-top with etalat, non-top without etalat, idx=0 reject, hand-size limits, multi-piece take)

```java
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
```

- [ ] **Step 2: Run (FAIL)**

- [ ] **Step 3: Implement `applyTakeDiscard`** (replace stub in `GameEngine.java`)

```java
  private static ActionResult applyTakeDiscard(GameState s, Action.TakeDiscard a) {
    if (s.phase() != Phase.DRAW) return reject(RejectReason.WRONG_PHASE, "Nu poți trage acum.");
    if (s.discard().isEmpty()) return reject(RejectReason.DISCARD_EMPTY, "Nu este nicio piesă aruncată.");
    int idx = a.discardIdx();
    if (idx < 0 || idx >= s.discard().size()) return reject(RejectReason.DISCARD_EMPTY, "Index discard invalid.");
    boolean isTop = (idx == s.discard().size() - 1);
    if (idx == 0 && s.discard().size() > 1)
      return reject(RejectReason.CANNOT_TAKE_OPENING_PIECE, "Nu poți lua piesa de start.");

    Player p = s.players().get(s.current());
    if (p.hand().size() <= 2 && !isTop)
      return reject(RejectReason.CANNOT_BREAK_LINE, "Prea puține piese pentru a rupe șirul.");
    if (p.hand().size() == 3 && !isTop)
      return reject(RejectReason.CANNOT_BREAK_LINE, "Cu 3 piese poți lua doar ultima.");
    if (!isTop) {
      if (!p.hasEtalat()) return reject(RejectReason.BREAK_REQUIRES_ETALAT, "Trebuie să fii etalat pentru a rupe șirul.");
      if (p.hand().size() < 4) return reject(RejectReason.CANNOT_BREAK_LINE, "Necesită ≥4 piese.");
    }

    var newDiscard = new java.util.ArrayList<>(s.discard());
    var taken = new java.util.ArrayList<Piece>();
    for (int i = newDiscard.size() - 1; i >= idx; i--) taken.add(0, newDiscard.remove(i));
    Piece chosen = taken.get(0);

    var newHand = new java.util.ArrayList<>(p.hand());
    newHand.addAll(taken);
    var newPlayer = new Player(p.name(), p.isBot(), newHand, p.hasEtalat(), p.calledAtu(), p.announced(), chosen.id());
    var newPlayers = new java.util.ArrayList<>(s.players()); newPlayers.set(s.current(), newPlayer);

    GameState ns = new GameState(s.id(), newPlayers, s.stock(), newDiscard, s.atu(), s.melds(),
        s.current(), Phase.ACTION, DrawSource.DISCARD, s.turnTaken(), s.round(), s.mode(), s.difficulty(),
        s.doubleGame(), s.closed(), s.totals(), s.seed());
    return accept(ns, new DomainEvent.DiscardTaken(s.current(), idx, taken.size()));
  }
```

- [ ] **Step 4: Run (PASS)** + **Step 5: Commit**

```bash
mvn -q test -Dtest=GameEngineTakeDiscardTest
git add src/main/java/com/remi/engine/rules/GameEngine.java src/test/java/com/remi/engine/rules/GameEngineTakeDiscardTest.java
git commit -m "feat(engine): GameEngine.applyTakeDiscard with break-line rules"
```

---

### Task D4: Apply `Etalat`

**Files:**
- Modify: `src/main/java/com/remi/engine/rules/GameEngine.java`
- Create: `src/test/java/com/remi/engine/rules/GameEngineEtalatTest.java`

JS reference: `tryEtalat()` `remi.html:1372-1410`. Rules:
- All proposed melds must be valid (`MeldValidator.isValid`)
- If !hasEtalat: total first-meld points ≥ 45 AND (at least one SUITE OR a GROUP containing num=1)
- All pieces must be in the player's hand
- On success: remove pieces from hand, append melds with owner=current, set hasEtalat=true; if mustUsePieceId is among them, clear it

- [ ] **Step 1: Write failing tests**

```java
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
```

- [ ] **Step 2: Run (FAIL)**

- [ ] **Step 3: Implement `applyEtalat`**

```java
  private static ActionResult applyEtalat(GameState s, Action.Etalat a) {
    if (s.phase() != Phase.ACTION && s.phase() != Phase.DISCARD)
      return reject(RejectReason.WRONG_PHASE, "Nu poți etala acum.");
    Player p = s.players().get(s.current());

    java.util.List<Meld> builtMelds = new java.util.ArrayList<>();
    java.util.Set<Integer> usedIds = new java.util.HashSet<>();
    java.util.Map<Integer, Piece> handIdx = new java.util.HashMap<>();
    for (Piece piece : p.hand()) handIdx.put(piece.id(), piece);

    for (MeldProposal mp : a.melds()) {
      java.util.List<Piece> pieces = new java.util.ArrayList<>();
      for (int pid : mp.pieceIds()) {
        if (!handIdx.containsKey(pid) || usedIds.contains(pid))
          return reject(RejectReason.PIECE_NOT_IN_HAND, "Piesă inexistentă în mână.");
        pieces.add(handIdx.get(pid));
        usedIds.add(pid);
      }
      java.util.Map<Integer, Integer> placedBy = new java.util.HashMap<>();
      for (Piece piece : pieces) placedBy.put(piece.id(), s.current());
      Meld m = new Meld(s.current(), mp.type(), pieces, placedBy);
      if (!MeldValidator.isValid(m))
        return reject(RejectReason.INVALID_MELD, "Combinație invalidă.");
      builtMelds.add(m);
    }

    if (!p.hasEtalat()) {
      int totalFirst = builtMelds.stream().mapToInt(m -> m.pieces().stream()
          .mapToInt(piece -> Scoring.firstMeldPieceValue(piece, m)).sum()).sum();
      boolean hasSuite = builtMelds.stream().anyMatch(m -> m.type() == MeldType.SUITE);
      boolean has1sGroup = builtMelds.stream().anyMatch(m ->
          m.type() == MeldType.GROUP && m.pieces().stream().anyMatch(pp -> !pp.isJoker() && pp.num() == 1));
      if (!hasSuite && !has1sGroup)
        return reject(RejectReason.FIRST_MELD_NEEDS_SUITE_OR_1S, "Prima etalare necesită o suită sau o terță de 1.");
      if (totalFirst < 45)
        return reject(RejectReason.FIRST_MELD_TOO_FEW_POINTS, "Prima etalare are " + totalFirst + "p < 45p.");
    }

    var newHand = new java.util.ArrayList<>(p.hand());
    newHand.removeIf(piece -> usedIds.contains(piece.id()));
    Integer newMustUse = p.mustUsePieceId();
    if (newMustUse != null && usedIds.contains(newMustUse)) newMustUse = null;

    var newPlayer = new Player(p.name(), p.isBot(), newHand, true, p.calledAtu(), p.announced(), newMustUse);
    var newPlayers = new java.util.ArrayList<>(s.players()); newPlayers.set(s.current(), newPlayer);
    var newMelds = new java.util.ArrayList<>(s.melds()); newMelds.addAll(builtMelds);

    int totalPts = builtMelds.stream().mapToInt(m -> m.pieces().stream()
        .mapToInt(piece -> Scoring.firstMeldPieceValue(piece, m)).sum()).sum();

    GameState ns = new GameState(s.id(), newPlayers, s.stock(), s.discard(), s.atu(), newMelds,
        s.current(), s.phase(), s.drewFrom(), s.turnTaken(), s.round(), s.mode(), s.difficulty(),
        s.doubleGame(), s.closed(), s.totals(), s.seed());
    return accept(ns, new DomainEvent.PlayerEtalat(s.current(), totalPts));
  }
```

- [ ] **Step 4: Run (PASS)** + **Step 5: Commit**

```bash
mvn -q test -Dtest=GameEngineEtalatTest
git add src/main/java/com/remi/engine/rules/GameEngine.java src/test/java/com/remi/engine/rules/GameEngineEtalatTest.java
git commit -m "feat(engine): GameEngine.applyEtalat with first-meld 45p+suite/1s rule"
```

---

### Task D5: Apply `Layoff`

**Files:**
- Modify: `src/main/java/com/remi/engine/rules/GameEngine.java`
- Create: `src/test/java/com/remi/engine/rules/GameEngineLayoffTest.java`

JS reference: `tryLayoff()` `remi.html:1412-1447`. Rules:
- Player must have hasEtalat
- For each layoff: try inserting piece at every position in target meld; if any position keeps the meld valid → place there
- All layoffs validated together; if any fails, reject all
- placedBy map updated for the new pieces

- [ ] **Step 1: Write failing tests**

```java
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
```

- [ ] **Step 2: Run (FAIL)**

- [ ] **Step 3: Implement `applyLayoff`**

```java
  private static ActionResult applyLayoff(GameState s, Action.Layoff a) {
    if (s.phase() != Phase.ACTION && s.phase() != Phase.DISCARD)
      return reject(RejectReason.WRONG_PHASE, "Nu poți lipi acum.");
    Player p = s.players().get(s.current());
    if (!p.hasEtalat()) return reject(RejectReason.NOT_ETALAT, "Trebuie să fii etalat.");

    java.util.Map<Integer, Piece> handIdx = new java.util.HashMap<>();
    for (Piece piece : p.hand()) handIdx.put(piece.id(), piece);

    java.util.List<Meld> meldsCopy = new java.util.ArrayList<>(s.melds());
    java.util.Set<Integer> placedIds = new java.util.HashSet<>();

    for (LayoffProposal lo : a.layoffs()) {
      Piece piece = handIdx.get(lo.pieceId());
      if (piece == null || placedIds.contains(lo.pieceId()))
        return reject(RejectReason.PIECE_NOT_IN_HAND, "Piesă inexistentă.");
      if (lo.meldIdx() < 0 || lo.meldIdx() >= meldsCopy.size())
        return reject(RejectReason.INVALID_LAYOFF, "Combinație inexistentă.");
      Meld target = meldsCopy.get(lo.meldIdx());
      Meld inserted = tryInsertIntoMeld(target, piece, s.current());
      if (inserted == null) return reject(RejectReason.INVALID_LAYOFF, "Piesa nu se potrivește.");
      meldsCopy.set(lo.meldIdx(), inserted);
      placedIds.add(lo.pieceId());
    }

    var newHand = new java.util.ArrayList<>(p.hand());
    newHand.removeIf(piece -> placedIds.contains(piece.id()));
    Integer newMustUse = p.mustUsePieceId();
    if (newMustUse != null && placedIds.contains(newMustUse)) newMustUse = null;

    var newPlayer = new Player(p.name(), p.isBot(), newHand, p.hasEtalat(), p.calledAtu(), p.announced(), newMustUse);
    var newPlayers = new java.util.ArrayList<>(s.players()); newPlayers.set(s.current(), newPlayer);

    GameState ns = new GameState(s.id(), newPlayers, s.stock(), s.discard(), s.atu(), meldsCopy,
        s.current(), s.phase(), s.drewFrom(), s.turnTaken(), s.round(), s.mode(), s.difficulty(),
        s.doubleGame(), s.closed(), s.totals(), s.seed());

    java.util.List<DomainEvent> evts = new java.util.ArrayList<>();
    for (LayoffProposal lo : a.layoffs())
      evts.add(new DomainEvent.LayoffPlayed(s.current(), lo.meldIdx(), lo.pieceId()));
    return new ActionResult.Accepted(ns, evts);
  }

  private static Meld tryInsertIntoMeld(Meld meld, Piece piece, int playerIdx) {
    for (int pos = 0; pos <= meld.pieces().size(); pos++) {
      var trial = new java.util.ArrayList<>(meld.pieces());
      trial.add(pos, piece);
      Meld candidate = new Meld(meld.owner(), meld.type(), trial, meld.placedBy());
      if (MeldValidator.isValid(candidate)) {
        var newPlacedBy = new java.util.HashMap<>(meld.placedBy());
        newPlacedBy.put(piece.id(), playerIdx);
        return new Meld(meld.owner(), meld.type(), trial, newPlacedBy);
      }
    }
    return null;
  }
```

- [ ] **Step 4: Run (PASS)** + **Step 5: Commit**

```bash
mvn -q test -Dtest=GameEngineLayoffTest
git add src/main/java/com/remi/engine/rules/GameEngine.java src/test/java/com/remi/engine/rules/GameEngineLayoffTest.java
git commit -m "feat(engine): GameEngine.applyLayoff with position-based insertion"
```

---

### Task D6: Apply `Discard` + turn end + round close

**Files:**
- Modify: `src/main/java/com/remi/engine/rules/GameEngine.java`
- Create: `src/test/java/com/remi/engine/rules/GameEngineDiscardTest.java`

JS reference: `discardPiece()` `remi.html:1333-1369` + `endTurn()` `remi.html:1218-1239`. Rules:
- Phase must be ACTION or DISCARD
- mustUsePieceId must have been played (PIECE removed from hand) before discarding
- After discard: if hand.length == 0 && hasEtalat → close round; else advance to next player (turnTaken+1, phase=DRAW)
- After discard, if hand.length ≤ 3 → set `announced=true`
- If hand.length ≤ 1 + hasEtalat after discard → close round
- If stock empty after switch → end round (stock empty)

- [ ] **Step 1: Write failing tests**

```java
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
```

- [ ] **Step 2: Run (FAIL)**

- [ ] **Step 3: Implement `applyDiscard` + helpers**

```java
  private static ActionResult applyDiscard(GameState s, Action.Discard a) {
    if (s.phase() != Phase.ACTION && s.phase() != Phase.DISCARD)
      return reject(RejectReason.WRONG_PHASE, "Nu poți arunca acum.");
    Player p = s.players().get(s.current());
    if (p.mustUsePieceId() != null && p.mustUsePieceId() == a.pieceId())
      return reject(RejectReason.MUST_USE_TAKEN_PIECE, "Trebuie să folosești piesa luată din șir.");
    if (p.mustUsePieceId() != null && p.hand().stream().anyMatch(piece -> piece.id() == p.mustUsePieceId()))
      return reject(RejectReason.MUST_USE_TAKEN_PIECE, "Trebuie să folosești piesa luată din șir.");

    int idx = -1;
    for (int i = 0; i < p.hand().size(); i++) if (p.hand().get(i).id() == a.pieceId()) { idx = i; break; }
    if (idx < 0) return reject(RejectReason.PIECE_NOT_IN_HAND, "Piesă inexistentă în mână.");

    var newHand = new java.util.ArrayList<>(p.hand());
    Piece discarded = newHand.remove(idx);
    var newDiscard = new java.util.ArrayList<>(s.discard()); newDiscard.add(discarded);

    boolean announce = p.announced() || newHand.size() <= 3;
    var newPlayer = new Player(p.name(), p.isBot(), newHand, p.hasEtalat(), p.calledAtu(), announce, null);
    var newPlayers = new java.util.ArrayList<>(s.players()); newPlayers.set(s.current(), newPlayer);

    GameState afterDiscard = new GameState(s.id(), newPlayers, s.stock(), newDiscard, s.atu(), s.melds(),
        s.current(), s.phase(), s.drewFrom(), s.turnTaken(), s.round(), s.mode(), s.difficulty(),
        s.doubleGame(), s.closed(), s.totals(), s.seed());

    java.util.List<DomainEvent> evts = new java.util.ArrayList<>();
    evts.add(new DomainEvent.PieceDiscarded(s.current(), discarded.id()));

    // Round-end checks
    if (newHand.isEmpty() && p.hasEtalat()) {
      return closeRound(afterDiscard, s.current(), null, evts);
    }
    if (newHand.size() == 1 && p.hasEtalat()) {
      return closeRound(afterDiscard, s.current(), newHand.get(0), evts);
    }
    if (afterDiscard.stock().isEmpty()) {
      return endRoundStockEmpty(afterDiscard, evts);
    }
    // Advance turn
    int next = (s.current() + 1) % s.players().size();
    GameState ns = new GameState(afterDiscard.id(), afterDiscard.players(), afterDiscard.stock(),
        afterDiscard.discard(), afterDiscard.atu(), afterDiscard.melds(),
        next, Phase.DRAW, null, s.turnTaken() + 1, s.round(), s.mode(), s.difficulty(),
        s.doubleGame(), false, s.totals(), s.seed());
    evts.add(new DomainEvent.TurnStarted(next));
    return new ActionResult.Accepted(ns, evts);
  }

  private static ActionResult closeRound(GameState s, int closerIdx, Piece lastDiscarded, java.util.List<DomainEvent> evts) {
    java.util.List<RoundResult> results = Scoring.closeRound(s, closerIdx, lastDiscarded);
    var newTotals = new java.util.ArrayList<>(s.totals());
    for (RoundResult r : results) newTotals.set(r.playerIdx(), newTotals.get(r.playerIdx()) + r.base());
    GameState ns = new GameState(s.id(), s.players(), s.stock(), s.discard(), s.atu(), s.melds(),
        s.current(), s.phase(), s.drewFrom(), s.turnTaken(), s.round(), s.mode(), s.difficulty(),
        s.doubleGame(), true, newTotals, s.seed());
    evts.add(new DomainEvent.RoundClosed(closerIdx, results, lastDiscarded != null && lastDiscarded.isJoker()));
    return new ActionResult.Accepted(ns, evts);
  }

  private static ActionResult endRoundStockEmpty(GameState s, java.util.List<DomainEvent> evts) {
    evts.add(new DomainEvent.StockExhausted());
    return closeRound(s, s.current(), null, evts);
  }
```

- [ ] **Step 4: Run (PASS)** + **Step 5: Commit**

```bash
mvn -q test -Dtest=GameEngineDiscardTest
git add src/main/java/com/remi/engine/rules/GameEngine.java src/test/java/com/remi/engine/rules/GameEngineDiscardTest.java
git commit -m "feat(engine): GameEngine.applyDiscard + turn-end + round-close logic"
```

---

### Task D7: Apply `ForceAutoAction`

**Files:**
- Modify: `src/main/java/com/remi/engine/rules/GameEngine.java`
- Create: `src/test/java/com/remi/engine/rules/GameEngineForceAutoTest.java`

JS reference: `forceAutoAction()` `remi.html:1133-1189`. Rules:
- Phase=DRAW + stock not empty → auto draw from stock
- Phase=DRAW + stock empty → end round (stock empty)
- Phase=ACTION/DISCARD → auto-discard highest-value non-joker non-mustUse; fallback to joker; fallback to anything (clearing mustUse)

- [ ] **Step 1: Write failing tests**

```java
package com.remi.engine.rules;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.GameStateBuilder;
import org.junit.jupiter.api.Test;
import static com.remi.engine.testdata.Pieces.*;
import static org.assertj.core.api.Assertions.assertThat;

class GameEngineForceAutoTest {
  @Test void forceAutoDrawsFromStockWhenPhaseDraw() {
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.DRAW).current(0).hand(0, p(5, Color.RED))
        .stock(p(2, Color.BLACK), p(3, Color.BLUE)).build();
    ActionResult r = GameEngine.apply(s, new Action.ForceAutoAction(0));
    GameState ns = ((ActionResult.Accepted) r).newState();
    assertThat(ns.phase()).isEqualTo(Phase.ACTION);
    assertThat(ns.players().get(0).hand()).hasSize(2);
  }
  @Test void forceAutoDiscardsHighestNonJokerNonMustUse() {
    Piece p5 = p(5, Color.RED), p13 = p(13, Color.BLUE), p1 = p(1, Color.RED);
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.ACTION).current(0).hand(0, p5, p13, p1)
        .hand(1, p(7, Color.YELLOW)).stock(p(2, Color.BLACK)).build();
    ActionResult r = GameEngine.apply(s, new Action.ForceAutoAction(0));
    GameState ns = ((ActionResult.Accepted) r).newState();
    // p1=25, p13=10, p5=5 → p1 has highest value
    assertThat(ns.discard()).contains(p1);
  }
  @Test void forceAutoDiscardSkipsMustUse() {
    Piece p5 = p(5, Color.RED), p13 = p(13, Color.BLUE);
    GameState s = GameStateBuilder.aGame().withPlayers("A","B")
        .phase(Phase.ACTION).current(0).hand(0, p5, p13).mustUse(0, p13.id())
        .hand(1, p(7, Color.YELLOW)).stock(p(2, Color.BLACK)).build();
    ActionResult r = GameEngine.apply(s, new Action.ForceAutoAction(0));
    GameState ns = ((ActionResult.Accepted) r).newState();
    assertThat(ns.discard()).contains(p5);
  }
}
```

- [ ] **Step 2: Run (FAIL)**

- [ ] **Step 3: Implement `applyForceAuto`**

```java
  private static ActionResult applyForceAuto(GameState s, Action.ForceAutoAction a) {
    if (s.phase() == Phase.DRAW) {
      if (s.stock().isEmpty()) return endRoundStockEmpty(s, new java.util.ArrayList<>());
      return applyDrawFromStock(s, new Action.DrawFromStock(s.current()));
    }
    Player p = s.players().get(s.current());
    if (p.hand().isEmpty()) return reject(RejectReason.WRONG_PHASE, "Mână goală.");

    Piece pick = null;
    for (Piece piece : p.hand()) {
      if (piece.isJoker()) continue;
      if (p.mustUsePieceId() != null && piece.id() == p.mustUsePieceId()) continue;
      if (pick == null || Scoring.finalPieceValue(piece) > Scoring.finalPieceValue(pick)) pick = piece;
    }
    if (pick == null) {
      for (Piece piece : p.hand()) {
        if (p.mustUsePieceId() != null && piece.id() == p.mustUsePieceId()) continue;
        if (pick == null || Scoring.finalPieceValue(piece) > Scoring.finalPieceValue(pick)) pick = piece;
      }
    }
    if (pick == null) pick = p.hand().get(0); // give up mustUse

    // Clear mustUse and call discard
    var newPlayer = new Player(p.name(), p.isBot(), p.hand(), p.hasEtalat(), p.calledAtu(), p.announced(), null);
    var newPlayers = new java.util.ArrayList<>(s.players()); newPlayers.set(s.current(), newPlayer);
    GameState cleared = new GameState(s.id(), newPlayers, s.stock(), s.discard(), s.atu(), s.melds(),
        s.current(), s.phase(), s.drewFrom(), s.turnTaken(), s.round(), s.mode(), s.difficulty(),
        s.doubleGame(), s.closed(), s.totals(), s.seed());
    return applyDiscard(cleared, new Action.Discard(s.current(), pick.id()));
  }
```

- [ ] **Step 4: Run all engine tests (all PASS)** + **Step 5: Commit**

```bash
mvn -q test -Dtest=GameEngine*
git add src/main/java/com/remi/engine/rules/GameEngine.java src/test/java/com/remi/engine/rules/GameEngineForceAutoTest.java
git commit -m "feat(engine): GameEngine.applyForceAuto for timer-expired auto-actions"
```

---

## Phase E — AI

### Task E1: `MeldFinder.findAnyMelds` + `findFirstMeldSet` + `findLayoffs`

**Files:**
- Create: `src/main/java/com/remi/engine/ai/MeldFinder.java`
- Create: `src/test/java/com/remi/engine/ai/MeldFinderTest.java`

JS reference: `findAnyMelds()` `remi.html:1696-1756`, `findFirstMeldSet()` `:1758-1792`, `findLayoffs()` `:1794-1814`.

- [ ] **Step 1: Write failing tests** (table-driven; cover: groups detected, suites detected, jokers used as fillers, first-meld 45p selection, layoffs onto existing melds)

```java
package com.remi.engine.ai;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.MeldBuilder;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.remi.engine.testdata.Pieces.*;
import static org.assertj.core.api.Assertions.assertThat;

class MeldFinderTest {
  @Test void findsGroupOfSameNumDifferentColors() {
    List<Piece> hand = List.of(p(7, Color.RED), p(7, Color.BLUE), p(7, Color.BLACK), p(2, Color.RED));
    List<Meld> melds = MeldFinder.findAnyMelds(hand);
    assertThat(melds).hasSize(1);
    assertThat(melds.get(0).type()).isEqualTo(MeldType.GROUP);
  }
  @Test void findsSuiteOfConsecutiveSameColor() {
    List<Piece> hand = List.of(p(5, Color.RED), p(6, Color.RED), p(7, Color.RED), p(2, Color.BLUE));
    List<Meld> melds = MeldFinder.findAnyMelds(hand);
    assertThat(melds).hasSize(1);
    assertThat(melds.get(0).type()).isEqualTo(MeldType.SUITE);
  }
  @Test void findFirstMeldSetReturnsNullIfBelow45() {
    List<Piece> hand = List.of(p(2, Color.RED), p(3, Color.RED), p(4, Color.RED));
    assertThat(MeldFinder.findFirstMeldSet(hand)).isNull();
  }
  @Test void findFirstMeldSetReturnsValid() {
    List<Piece> hand = List.of(
        p(10, Color.RED), p(11, Color.RED), p(12, Color.RED), p(13, Color.RED),  // suite 10-13 = 40
        p(1, Color.RED), p(1, Color.BLUE), p(1, Color.BLACK)                       // group of 1s = 75
    );
    List<Meld> chosen = MeldFinder.findFirstMeldSet(hand);
    assertThat(chosen).isNotNull();
    assertThat(chosen).hasSizeGreaterThanOrEqualTo(1);
  }
  @Test void findLayoffsExtendsExistingSuite() {
    Piece p4 = p(4, Color.RED);
    Meld m = MeldBuilder.suite(p(5, Color.RED), p(6, Color.RED), p(7, Color.RED)).owner(1).build();
    List<LayoffProposal> los = MeldFinder.findLayoffs(List.of(p4), List.of(m));
    assertThat(los).hasSize(1);
    assertThat(los.get(0).pieceId()).isEqualTo(p4.id());
    assertThat(los.get(0).meldIdx()).isEqualTo(0);
  }
}
```

- [ ] **Step 2: Run (FAIL)**

- [ ] **Step 3: Implement `MeldFinder.java`** (port from JS lines 1696-1814)

```java
package com.remi.engine.ai;

import com.remi.engine.domain.*;
import com.remi.engine.rules.MeldValidator;
import com.remi.engine.rules.Scoring;
import java.util.*;

public final class MeldFinder {
  private MeldFinder() {}

  public static List<Meld> findAnyMelds(List<Piece> hand) {
    List<Meld> found = new ArrayList<>();
    Set<Integer> used = new HashSet<>();

    // Groups by num
    Map<Integer, List<Piece>> byNum = new HashMap<>();
    for (Piece p : hand) {
      if (p.isJoker()) continue;
      byNum.computeIfAbsent(p.num(), k -> new ArrayList<>()).add(p);
    }
    Deque<Piece> jokers = new ArrayDeque<>();
    for (Piece p : hand) if (p.isJoker() && !used.contains(p.id())) jokers.add(p);

    for (var entry : byNum.entrySet()) {
      List<Piece> avail = new ArrayList<>();
      for (Piece p : entry.getValue()) if (!used.contains(p.id())) avail.add(p);
      Map<Color, Piece> byColor = new HashMap<>();
      for (Piece p : avail) byColor.putIfAbsent(p.color(), p);
      List<Piece> distinct = new ArrayList<>(byColor.values());
      if (distinct.size() >= 3) {
        List<Piece> grp = distinct.subList(0, Math.min(4, distinct.size()));
        Map<Integer, Integer> placedBy = new HashMap<>();
        Meld m = new Meld(0, MeldType.GROUP, grp, placedBy);
        if (MeldValidator.isValid(m)) {
          grp.forEach(p -> used.add(p.id()));
          found.add(m);
        }
      } else if (distinct.size() == 2 && !jokers.isEmpty()) {
        Piece j = jokers.pop();
        List<Piece> grp = new ArrayList<>(distinct); grp.add(j);
        Meld m = new Meld(0, MeldType.GROUP, grp, new HashMap<>());
        if (MeldValidator.isValid(m)) {
          grp.forEach(p -> used.add(p.id()));
          found.add(m);
        }
      }
    }

    // Suites by color
    Map<Color, List<Piece>> byColor = new HashMap<>();
    for (Piece p : hand) {
      if (p.isJoker() || used.contains(p.id())) continue;
      byColor.computeIfAbsent(p.color(), k -> new ArrayList<>()).add(p);
    }
    for (var entry : byColor.entrySet()) {
      List<Piece> list = new ArrayList<>(entry.getValue());
      list.sort(Comparator.comparingInt(Piece::num));
      List<Piece> run = new ArrayList<>();
      for (Piece p : list) {
        if (run.isEmpty() || p.num() == run.get(run.size() - 1).num() + 1) run.add(p);
        else if (p.num() == run.get(run.size() - 1).num()) continue;
        else {
          if (run.size() >= 3) {
            Meld m = new Meld(0, MeldType.SUITE, new ArrayList<>(run), new HashMap<>());
            run.forEach(pp -> used.add(pp.id()));
            found.add(m);
          }
          run = new ArrayList<>(); run.add(p);
        }
      }
      if (run.size() >= 3) {
        Meld m = new Meld(0, MeldType.SUITE, new ArrayList<>(run), new HashMap<>());
        run.forEach(pp -> used.add(pp.id()));
        found.add(m);
      }
    }
    return found;
  }

  public static List<Meld> findFirstMeldSet(List<Piece> hand) {
    List<Meld> all = findAnyMelds(hand);
    List<Meld> suites = new ArrayList<>();
    List<Meld> groups = new ArrayList<>();
    for (Meld m : all) { if (m.type() == MeldType.SUITE) suites.add(m); else groups.add(m); }
    List<Meld> groupsDe1 = new ArrayList<>();
    for (Meld m : groups) if (m.pieces().stream().anyMatch(p -> !p.isJoker() && p.num() == 1)) groupsDe1.add(m);

    if (!suites.isEmpty()) {
      suites.sort((a, b) -> totalFirst(b) - totalFirst(a));
      List<Meld> chosen = new ArrayList<>(); chosen.add(suites.get(0));
      int total = totalFirst(suites.get(0));
      List<Meld> candidates = new ArrayList<>();
      candidates.addAll(suites.subList(1, suites.size()));
      candidates.addAll(groups);
      candidates.sort((a, b) -> totalFirst(b) - totalFirst(a));
      for (Meld c : candidates) {
        if (total >= 45) break;
        chosen.add(c); total += totalFirst(c);
      }
      if (total >= 45) return chosen;
    }
    if (!groupsDe1.isEmpty()) {
      List<Meld> chosen = new ArrayList<>(); chosen.add(groupsDe1.get(0));
      int total = totalFirst(groupsDe1.get(0));
      List<Meld> rest = new ArrayList<>();
      for (Meld m : groups) if (m != groupsDe1.get(0)) rest.add(m);
      rest.addAll(suites);
      for (Meld c : rest) {
        if (total >= 45) break;
        chosen.add(c); total += totalFirst(c);
      }
      if (total >= 45) return chosen;
    }
    return null;
  }

  private static int totalFirst(Meld m) {
    return m.pieces().stream().mapToInt(p -> Scoring.firstMeldPieceValue(p, m)).sum();
  }

  public static List<LayoffProposal> findLayoffs(List<Piece> hand, List<Meld> melds) {
    List<LayoffProposal> out = new ArrayList<>();
    Set<Integer> usedIds = new HashSet<>();
    for (int mi = 0; mi < melds.size(); mi++) {
      Meld meld = melds.get(mi);
      for (Piece piece : hand) {
        if (usedIds.contains(piece.id())) continue;
        for (int pos = 0; pos <= meld.pieces().size(); pos++) {
          List<Piece> trial = new ArrayList<>(meld.pieces());
          trial.add(pos, piece);
          Meld candidate = new Meld(meld.owner(), meld.type(), trial, meld.placedBy());
          if (MeldValidator.isValid(candidate)) {
            out.add(new LayoffProposal(piece.id(), mi));
            usedIds.add(piece.id());
            break;
          }
        }
      }
    }
    return out;
  }
}
```

- [ ] **Step 4: Run (PASS)** + **Step 5: Commit**

```bash
mvn -q test -Dtest=MeldFinderTest
git add src/main/java/com/remi/engine/ai/MeldFinder.java src/test/java/com/remi/engine/ai/MeldFinderTest.java
git commit -m "feat(ai): MeldFinder — findAnyMelds, findFirstMeldSet, findLayoffs"
```

---

### Task E2: `Bot.decide` — port `aiPlay`

**Files:**
- Create: `src/main/java/com/remi/engine/ai/Bot.java`
- Create: `src/test/java/com/remi/engine/ai/BotTest.java`

JS reference: `aiPlay()` `remi.html:1569-1661`. Per-call decision (one action), driven by phase + mode:
- DRAW phase → `DrawFromStock` (always; AI keeps it simple)
- ACTION + mode=TABLA: if `analyzeHandForClose` says canClose → emit close-style discard; else fall to discard
- ACTION + mode=ETALAT: if !hasEtalat and `findFirstMeldSet` non-null → `Etalat`; if hasEtalat → try more melds + layoffs
- Final fallback: `Discard` (highest-value non-joker non-mustUse)

Note: We split `aiPlay` (which does multiple actions in one JS turn) into per-action `decide` calls. The service loops `decide` until phase changes to next player.

- [ ] **Step 1: Write failing tests**

```java
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
```

- [ ] **Step 2: Run (FAIL)**

- [ ] **Step 3: Implement `Bot.java`**

```java
package com.remi.engine.ai;

import com.remi.engine.domain.*;
import com.remi.engine.rules.Scoring;
import java.util.*;

public final class Bot {
  private Bot() {}

  public static Action decide(GameState state, int playerIdx) {
    Player p = state.players().get(playerIdx);
    if (state.phase() == Phase.DRAW) {
      return new Action.DrawFromStock(playerIdx);
    }

    if (state.mode() == Mode.TABLA) {
      List<Meld> melds = MeldFinder.findAnyMelds(p.hand());
      Set<Integer> usedIds = new HashSet<>();
      for (Meld m : melds) for (Piece piece : m.pieces()) usedIds.add(piece.id());
      List<Piece> uncovered = new ArrayList<>();
      for (Piece piece : p.hand()) if (!usedIds.contains(piece.id())) uncovered.add(piece);
      if (!melds.isEmpty() && uncovered.size() <= 1) {
        if (uncovered.isEmpty()) return new Action.Discard(playerIdx, p.hand().get(0).id());
        return new Action.Discard(playerIdx, uncovered.get(0).id());
      }
    } else {
      if (!p.hasEtalat()) {
        List<Meld> first = MeldFinder.findFirstMeldSet(p.hand());
        if (first != null) {
          List<MeldProposal> proposals = new ArrayList<>();
          for (Meld m : first) {
            List<Integer> ids = new ArrayList<>();
            for (Piece piece : m.pieces()) ids.add(piece.id());
            proposals.add(new MeldProposal(m.type(), ids));
          }
          return new Action.Etalat(playerIdx, proposals);
        }
      } else {
        List<Meld> more = MeldFinder.findAnyMelds(p.hand());
        if (!more.isEmpty()) {
          Meld m = more.get(0);
          List<Integer> ids = new ArrayList<>();
          for (Piece piece : m.pieces()) ids.add(piece.id());
          return new Action.Etalat(playerIdx, List.of(new MeldProposal(m.type(), ids)));
        }
        List<LayoffProposal> los = MeldFinder.findLayoffs(p.hand(), state.melds());
        if (!los.isEmpty()) return new Action.Layoff(playerIdx, los);
      }
    }

    // Fallback: discard
    Piece pick = chooseDiscard(p);
    return new Action.Discard(playerIdx, pick.id());
  }

  static Piece chooseDiscard(Player p) {
    Piece best = null;
    for (Piece piece : p.hand()) {
      if (piece.isJoker()) continue;
      if (p.mustUsePieceId() != null && piece.id() == p.mustUsePieceId()) continue;
      if (best == null || Scoring.finalPieceValue(piece) > Scoring.finalPieceValue(best)) best = piece;
    }
    return best != null ? best : p.hand().get(0);
  }
}
```

- [ ] **Step 4: Run (PASS)** + **Step 5: Commit**

```bash
mvn -q test -Dtest=BotTest
git add src/main/java/com/remi/engine/ai/Bot.java src/test/java/com/remi/engine/ai/BotTest.java
git commit -m "feat(ai): Bot.decide — per-action AI port of aiPlay()"
```

---

## Continuă în Part 4

Phase F (golden playthrough + property tests), G (persistență), H (service), I (REST), J (E2E + coverage) — în `2026-05-16-stage1-game-engine-part4.md`.
