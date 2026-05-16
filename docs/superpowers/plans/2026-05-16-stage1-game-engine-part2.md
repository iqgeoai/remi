# Stage 1 — Game Engine Implementation Plan (Part 2: Rules + Engine + AI)

> **For agentic workers:** Continuation of `2026-05-16-stage1-game-engine.md`. Execute task-by-task with superpowers:subagent-driven-development or superpowers:executing-plans.

**Pre-requisite:** Part 1 (Phases A + B) completed — Maven scaffold, application config, Flyway V1, all domain records/enums in `com.remi.engine.domain`.

---

## Phase C — Test Data Builders + Rules

### Task C1: Test data builders (Piece, Meld, GameState)

**Files:**
- Create: `src/test/java/com/remi/engine/testdata/Pieces.java`
- Create: `src/test/java/com/remi/engine/testdata/MeldBuilder.java`
- Create: `src/test/java/com/remi/engine/testdata/GameStateBuilder.java`

These are essential for readable tests. Without them every test is 30 lines of boilerplate.

- [ ] **Step 1: Write `Pieces.java` (factory helpers)**

```java
package com.remi.engine.testdata;

import com.remi.engine.domain.Color;
import com.remi.engine.domain.Piece;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class Pieces {
  private static final AtomicInteger SEQ = new AtomicInteger(0);
  private Pieces() {}
  public static int nextId() { return SEQ.getAndIncrement(); }
  public static Piece p(int num, Color color) { return new Piece(nextId(), num, color, false); }
  public static Piece joker() { return new Piece(nextId(), 0, Color.JOKER, true); }
  public static List<Piece> hand(Piece... pieces) { return new ArrayList<>(List.of(pieces)); }
}
```

- [ ] **Step 2: Write `MeldBuilder.java`**

```java
package com.remi.engine.testdata;

import com.remi.engine.domain.*;
import java.util.*;

public final class MeldBuilder {
  private int owner = 0;
  private MeldType type = MeldType.GROUP;
  private final List<Piece> pieces = new ArrayList<>();
  private final Map<Integer, Integer> placedBy = new HashMap<>();

  public static MeldBuilder group(Piece... ps) {
    MeldBuilder b = new MeldBuilder();
    b.type = MeldType.GROUP;
    Collections.addAll(b.pieces, ps);
    return b;
  }
  public static MeldBuilder suite(Piece... ps) {
    MeldBuilder b = new MeldBuilder();
    b.type = MeldType.SUITE;
    Collections.addAll(b.pieces, ps);
    return b;
  }
  public MeldBuilder owner(int o) { this.owner = o; return this; }
  public MeldBuilder placedBy(int pieceId, int playerIdx) { placedBy.put(pieceId, playerIdx); return this; }
  public Meld build() { return new Meld(owner, type, pieces, placedBy); }
}
```

- [ ] **Step 3: Write `GameStateBuilder.java`**

```java
package com.remi.engine.testdata;

import com.remi.engine.domain.*;
import java.util.*;

public final class GameStateBuilder {
  private UUID id = UUID.randomUUID();
  private final List<Player> players = new ArrayList<>();
  private List<Piece> stock = new ArrayList<>();
  private List<Piece> discard = new ArrayList<>();
  private Piece atu = new Piece(-1, 5, Color.RED, false);
  private final List<Meld> melds = new ArrayList<>();
  private int current = 0;
  private Phase phase = Phase.DRAW;
  private DrawSource drewFrom = null;
  private int turnTaken = 0;
  private int round = 1;
  private Mode mode = Mode.ETALAT;
  private Difficulty difficulty = Difficulty.MED;
  private boolean doubleGame = false;
  private boolean closed = false;
  private List<Integer> totals = new ArrayList<>();
  private long seed = 42L;

  public static GameStateBuilder aGame() { return new GameStateBuilder(); }
  public GameStateBuilder withPlayers(String... names) {
    for (int i = 0; i < names.length; i++) {
      players.add(new Player(names[i], i > 0, new ArrayList<>(), false, false, false, null));
      totals.add(0);
    }
    return this;
  }
  public GameStateBuilder hand(int playerIdx, Piece... pieces) {
    Player p = players.get(playerIdx);
    players.set(playerIdx, new Player(p.name(), p.isBot(), List.of(pieces), p.hasEtalat(), p.calledAtu(), p.announced(), p.mustUsePieceId()));
    return this;
  }
  public GameStateBuilder etalat(int playerIdx) {
    Player p = players.get(playerIdx);
    players.set(playerIdx, new Player(p.name(), p.isBot(), p.hand(), true, p.calledAtu(), p.announced(), p.mustUsePieceId()));
    return this;
  }
  public GameStateBuilder mustUse(int playerIdx, int pieceId) {
    Player p = players.get(playerIdx);
    players.set(playerIdx, new Player(p.name(), p.isBot(), p.hand(), p.hasEtalat(), p.calledAtu(), p.announced(), pieceId));
    return this;
  }
  public GameStateBuilder stock(Piece... ps) { this.stock = new ArrayList<>(List.of(ps)); return this; }
  public GameStateBuilder discard(Piece... ps) { this.discard = new ArrayList<>(List.of(ps)); return this; }
  public GameStateBuilder atu(Piece p) { this.atu = p; return this; }
  public GameStateBuilder melds(Meld... ms) { melds.clear(); Collections.addAll(melds, ms); return this; }
  public GameStateBuilder current(int idx) { this.current = idx; return this; }
  public GameStateBuilder phase(Phase ph) { this.phase = ph; return this; }
  public GameStateBuilder drewFrom(DrawSource ds) { this.drewFrom = ds; return this; }
  public GameStateBuilder turnTaken(int n) { this.turnTaken = n; return this; }
  public GameStateBuilder mode(Mode m) { this.mode = m; return this; }
  public GameStateBuilder doubleGame(boolean d) { this.doubleGame = d; return this; }
  public GameState build() {
    return new GameState(id, players, stock, discard, atu, melds, current, phase, drewFrom,
        turnTaken, round, mode, difficulty, doubleGame, closed, totals, seed);
  }
}
```

- [ ] **Step 4: Compile tests + Commit**

```bash
mvn -q test-compile
git add src/test/java/com/remi/engine/testdata/
git commit -m "test(engine): add Pieces, MeldBuilder, GameStateBuilder test helpers"
```

---

### Task C2: `MeldValidator` — GROUP rules

**Files:**
- Create: `src/main/java/com/remi/engine/rules/MeldValidator.java`
- Create: `src/test/java/com/remi/engine/rules/MeldValidatorGroupTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.remi.engine.rules;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.MeldBuilder;
import org.junit.jupiter.api.Test;
import static com.remi.engine.testdata.Pieces.*;
import static org.assertj.core.api.Assertions.assertThat;

class MeldValidatorGroupTest {
  @Test void validGroupOf3DifferentColors() {
    Meld m = MeldBuilder.group(p(7, Color.RED), p(7, Color.BLUE), p(7, Color.BLACK)).build();
    assertThat(MeldValidator.isValid(m)).isTrue();
  }
  @Test void validGroupOf4DifferentColors() {
    Meld m = MeldBuilder.group(p(7, Color.RED), p(7, Color.BLUE), p(7, Color.BLACK), p(7, Color.YELLOW)).build();
    assertThat(MeldValidator.isValid(m)).isTrue();
  }
  @Test void invalidGroupOf5() {
    Meld m = MeldBuilder.group(p(7, Color.RED), p(7, Color.BLUE), p(7, Color.BLACK), p(7, Color.YELLOW), p(7, Color.RED)).build();
    assertThat(MeldValidator.isValid(m)).isFalse();
  }
  @Test void invalidGroupSameColor() {
    Meld m = MeldBuilder.group(p(7, Color.RED), p(7, Color.RED), p(7, Color.BLUE)).build();
    assertThat(MeldValidator.isValid(m)).isFalse();
  }
  @Test void invalidGroupMixedNumbers() {
    Meld m = MeldBuilder.group(p(7, Color.RED), p(8, Color.BLUE), p(7, Color.BLACK)).build();
    assertThat(MeldValidator.isValid(m)).isFalse();
  }
  @Test void invalidGroupTooShort() {
    Meld m = MeldBuilder.group(p(7, Color.RED), p(7, Color.BLUE)).build();
    assertThat(MeldValidator.isValid(m)).isFalse();
  }
  @Test void validGroupWithOneJoker() {
    Meld m = MeldBuilder.group(p(7, Color.RED), p(7, Color.BLUE), joker()).build();
    assertThat(MeldValidator.isValid(m)).isTrue();
  }
  @Test void invalidGroupAllJokers() {
    Meld m = MeldBuilder.group(joker(), joker(), joker()).build();
    assertThat(MeldValidator.isValid(m)).isFalse();
  }
}
```

- [ ] **Step 2: Run (should FAIL)**

Run: `mvn -q test -Dtest=MeldValidatorGroupTest`
Expected: compilation failure (MeldValidator missing).

- [ ] **Step 3: Write minimal `MeldValidator.java`**

```java
package com.remi.engine.rules;

import com.remi.engine.domain.Meld;
import com.remi.engine.domain.MeldType;
import com.remi.engine.domain.Piece;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MeldValidator {
  private MeldValidator() {}

  public static boolean isValid(Meld meld) {
    if (meld.pieces() == null || meld.pieces().size() < 3) return false;
    List<Piece> pieces = meld.pieces();
    long jokerCount = pieces.stream().filter(Piece::isJoker).count();
    long realCount = pieces.size() - jokerCount;
    if (realCount < 2) return false;

    return switch (meld.type()) {
      case GROUP -> isValidGroup(pieces);
      case SUITE -> isValidSuite(pieces);
    };
  }

  private static boolean isValidGroup(List<Piece> pieces) {
    if (pieces.size() > 4) return false;
    List<Piece> reals = pieces.stream().filter(p -> !p.isJoker()).toList();
    int num = reals.get(0).num();
    if (!reals.stream().allMatch(p -> p.num() == num)) return false;
    Set<com.remi.engine.domain.Color> colors = new HashSet<>();
    for (Piece p : reals) colors.add(p.color());
    return colors.size() == reals.size();
  }

  private static boolean isValidSuite(List<Piece> pieces) {
    return false; // implemented in next task
  }
}
```

- [ ] **Step 4: Run tests (group tests should PASS, suite stub returns false)**

Run: `mvn -q test -Dtest=MeldValidatorGroupTest`
Expected: BUILD SUCCESS, 8 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/remi/engine/rules/MeldValidator.java src/test/java/com/remi/engine/rules/MeldValidatorGroupTest.java
git commit -m "feat(engine): MeldValidator — GROUP rules (color+num+joker constraints)"
```

---

### Task C3: `MeldValidator` — SUITE rules (with 12-13-1 wrap)

**Files:**
- Modify: `src/main/java/com/remi/engine/rules/MeldValidator.java`
- Create: `src/test/java/com/remi/engine/rules/MeldValidatorSuiteTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.remi.engine.rules;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.MeldBuilder;
import org.junit.jupiter.api.Test;
import static com.remi.engine.testdata.Pieces.*;
import static org.assertj.core.api.Assertions.assertThat;

class MeldValidatorSuiteTest {
  @Test void validSuiteOf3Consecutive() {
    Meld m = MeldBuilder.suite(p(5, Color.RED), p(6, Color.RED), p(7, Color.RED)).build();
    assertThat(MeldValidator.isValid(m)).isTrue();
  }
  @Test void invalidSuiteMixedColors() {
    Meld m = MeldBuilder.suite(p(5, Color.RED), p(6, Color.BLUE), p(7, Color.RED)).build();
    assertThat(MeldValidator.isValid(m)).isFalse();
  }
  @Test void invalidSuiteNonConsecutive() {
    Meld m = MeldBuilder.suite(p(5, Color.RED), p(7, Color.RED), p(8, Color.RED)).build();
    assertThat(MeldValidator.isValid(m)).isFalse();
  }
  @Test void validSuiteWithJokerInMiddle() {
    Meld m = MeldBuilder.suite(p(5, Color.RED), joker(), p(7, Color.RED)).build();
    assertThat(MeldValidator.isValid(m)).isTrue();
  }
  @Test void validSuiteWithJokerAtStart() {
    Meld m = MeldBuilder.suite(joker(), p(6, Color.RED), p(7, Color.RED)).build();
    assertThat(MeldValidator.isValid(m)).isTrue();
  }
  @Test void validSuiteWrap12_13_1() {
    Meld m = MeldBuilder.suite(p(12, Color.RED), p(13, Color.RED), p(1, Color.RED)).build();
    assertThat(MeldValidator.isValid(m)).isTrue();
  }
  @Test void invalidSuiteOverflowPast14() {
    Meld m = MeldBuilder.suite(p(12, Color.RED), p(13, Color.RED), p(1, Color.RED), p(2, Color.RED)).build();
    assertThat(MeldValidator.isValid(m)).isFalse();
  }
  @Test void invalidSuiteTooShort() {
    Meld m = MeldBuilder.suite(p(5, Color.RED), p(6, Color.RED)).build();
    assertThat(MeldValidator.isValid(m)).isFalse();
  }
}
```

- [ ] **Step 2: Run (should FAIL except the trivially-false ones)**

Run: `mvn -q test -Dtest=MeldValidatorSuiteTest`
Expected: ~3 failures (wrap, valid-with-joker, valid-consecutive).

- [ ] **Step 3: Implement `isValidSuite` (replace stub)**

In `MeldValidator.java` replace the `isValidSuite` method:

```java
  private static boolean isValidSuite(List<Piece> pieces) {
    if (pieces.size() > 13) return false;
    List<Piece> reals = pieces.stream().filter(p -> !p.isJoker()).toList();
    com.remi.engine.domain.Color color = reals.get(0).color();
    if (!reals.stream().allMatch(p -> p.color() == color)) return false;

    Integer base = null;
    for (int i = 0; i < pieces.size(); i++) {
      Piece p = pieces.get(i);
      if (p.isJoker()) continue;
      int candidate = p.num() - i;
      if (base == null) { base = candidate; continue; }
      if (candidate == base) continue;
      if ((p.num() + 13 - i) == base) continue;
      return false;
    }
    if (base == null) return false;

    for (int i = 0; i < pieces.size(); i++) {
      int n = base + i;
      if (n > 14) return false;
      if (n == 14) continue; // wrap (e.g., 12-13-1)
      if (n < 1) return false;
    }
    return true;
  }
```

- [ ] **Step 4: Run tests (should PASS)**

Run: `mvn -q test -Dtest=MeldValidatorSuiteTest`
Expected: BUILD SUCCESS, 8 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/remi/engine/rules/MeldValidator.java src/test/java/com/remi/engine/rules/MeldValidatorSuiteTest.java
git commit -m "feat(engine): MeldValidator — SUITE rules with 12-13-1 wrap"
```

---

### Task C4: `Scoring` — final + first-meld piece values

**Files:**
- Create: `src/main/java/com/remi/engine/rules/Scoring.java`
- Create: `src/test/java/com/remi/engine/rules/ScoringPieceValueTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.remi.engine.rules;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.MeldBuilder;
import org.junit.jupiter.api.Test;
import static com.remi.engine.testdata.Pieces.*;
import static org.assertj.core.api.Assertions.assertThat;

class ScoringPieceValueTest {
  @Test void finalValueJokerIs50()       { assertThat(Scoring.finalPieceValue(joker())).isEqualTo(50); }
  @Test void finalValue1Is25()           { assertThat(Scoring.finalPieceValue(p(1, Color.RED))).isEqualTo(25); }
  @Test void finalValue5Is5()            { assertThat(Scoring.finalPieceValue(p(5, Color.RED))).isEqualTo(5); }
  @Test void finalValue9Is5()            { assertThat(Scoring.finalPieceValue(p(9, Color.RED))).isEqualTo(5); }
  @Test void finalValue10Is10()          { assertThat(Scoring.finalPieceValue(p(10, Color.RED))).isEqualTo(10); }
  @Test void finalValue13Is10()          { assertThat(Scoring.finalPieceValue(p(13, Color.RED))).isEqualTo(10); }

  @Test void firstMeldValue1InGroupIs25() {
    Piece one = p(1, Color.RED);
    Meld m = MeldBuilder.group(one, p(1, Color.BLUE), p(1, Color.BLACK)).build();
    assertThat(Scoring.firstMeldPieceValue(one, m)).isEqualTo(25);
  }
  @Test void firstMeldValue1AtStartOfSuiteIs5() {
    Piece one = p(1, Color.RED);
    Meld m = MeldBuilder.suite(one, p(2, Color.RED), p(3, Color.RED)).build();
    assertThat(Scoring.firstMeldPieceValue(one, m)).isEqualTo(5);
  }
  @Test void firstMeldValue1AtEndOfSuiteIs10() {
    Piece one = p(1, Color.RED);
    Meld m = MeldBuilder.suite(p(12, Color.RED), p(13, Color.RED), one).build();
    assertThat(Scoring.firstMeldPieceValue(one, m)).isEqualTo(10);
  }
  @Test void firstMeldJokerInGroupOf7sIs5() {
    Piece j = joker();
    Meld m = MeldBuilder.group(p(7, Color.RED), p(7, Color.BLUE), j).build();
    assertThat(Scoring.firstMeldPieceValue(j, m)).isEqualTo(5);
  }
  @Test void firstMeldJokerInGroupOf10sIs10() {
    Piece j = joker();
    Meld m = MeldBuilder.group(p(10, Color.RED), p(10, Color.BLUE), j).build();
    assertThat(Scoring.firstMeldPieceValue(j, m)).isEqualTo(10);
  }
  @Test void firstMeldJokerInGroupOf1sIs25() {
    Piece j = joker();
    Meld m = MeldBuilder.group(p(1, Color.RED), p(1, Color.BLUE), j).build();
    assertThat(Scoring.firstMeldPieceValue(j, m)).isEqualTo(25);
  }
}
```

- [ ] **Step 2: Run (FAIL)**

Run: `mvn -q test -Dtest=ScoringPieceValueTest`

- [ ] **Step 3: Implement `Scoring.java`**

```java
package com.remi.engine.rules;

import com.remi.engine.domain.*;
import java.util.List;

public final class Scoring {
  private Scoring() {}

  public static int finalPieceValue(Piece p) {
    if (p.isJoker()) return 50;
    if (p.num() == 1) return 25;
    if (p.num() >= 2 && p.num() <= 9) return 5;
    return 10;
  }

  public static int firstMeldPieceValue(Piece piece, Meld meld) {
    if (!piece.isJoker() && piece.num() >= 2 && piece.num() <= 9) return 5;
    if (!piece.isJoker() && piece.num() >= 10 && piece.num() <= 13) return 10;
    if (!piece.isJoker() && piece.num() == 1) {
      if (meld.type() == MeldType.GROUP) return 25;
      int idx = meld.pieces().indexOf(piece);
      if (idx == 0) return 5;
      if (idx == meld.pieces().size() - 1) return 10;
      return 5;
    }
    // joker
    if (meld.type() == MeldType.GROUP) {
      Piece ref = meld.pieces().stream().filter(p -> !p.isJoker()).findFirst().orElse(null);
      if (ref == null) return 10;
      if (ref.num() == 1) return 25;
      if (ref.num() >= 2 && ref.num() <= 9) return 5;
      return 10;
    }
    Integer repNum = inferJokerNumber(meld, piece);
    if (repNum != null && repNum == 1) {
      int idx = meld.pieces().indexOf(piece);
      return idx == 0 ? 5 : 10;
    }
    if (repNum != null && repNum >= 2 && repNum <= 9) return 5;
    return 10;
  }

  public static Integer inferJokerNumber(Meld meld, Piece jokerPiece) {
    if (meld.type() == MeldType.GROUP) {
      Piece ref = meld.pieces().stream().filter(p -> !p.isJoker()).findFirst().orElse(null);
      return ref == null ? null : ref.num();
    }
    int idx = meld.pieces().indexOf(jokerPiece);
    List<Piece> pieces = meld.pieces();
    for (int i = 0; i < pieces.size(); i++) {
      if (!pieces.get(i).isJoker()) {
        int diff = idx - i;
        int n = pieces.get(i).num() + diff;
        if (n > 13) n = ((n - 1) % 13) + 1;
        if (n < 1) n = 13 - (-n);
        return n;
      }
    }
    return null;
  }
}
```

- [ ] **Step 4: Run tests (PASS)** + **Step 5: Commit**

```bash
mvn -q test -Dtest=ScoringPieceValueTest
git add src/main/java/com/remi/engine/rules/Scoring.java src/test/java/com/remi/engine/rules/ScoringPieceValueTest.java
git commit -m "feat(engine): Scoring — final + first-meld piece values + joker inference"
```

---

### Task C5: `Scoring.closeRound` — etalat mode

**Files:**
- Modify: `src/main/java/com/remi/engine/rules/Scoring.java`
- Create: `src/test/java/com/remi/engine/rules/ScoringCloseRoundEtalatTest.java`

- [ ] **Step 1: Write failing tests** (cover: closing bonus +50, atu +50, not-etalat -100, joker-close ×2, joc-dublu ×2)

```java
package com.remi.engine.rules;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.remi.engine.testdata.Pieces.*;
import static org.assertj.core.api.Assertions.assertThat;

class ScoringCloseRoundEtalatTest {
  @Test void notEtalatPenaltyMinus100() {
    GameState s = GameStateBuilder.aGame().withPlayers("A", "B")
        .hand(0, p(5, Color.RED), p(6, Color.RED))
        .hand(1, p(7, Color.BLUE)).etalat(1)
        .build();
    List<RoundResult> r = Scoring.closeRound(s, 1, null);
    assertThat(r.get(0).base()).isEqualTo(-100);
  }
  @Test void closingBonusPlus50() {
    GameState s = GameStateBuilder.aGame().withPlayers("A", "B")
        .hand(0, p(5, Color.RED)).etalat(0)
        .hand(1, p(7, Color.BLUE)).etalat(1)
        .melds(MeldBuilder.group(p(7,Color.RED), p(7,Color.BLUE), p(7,Color.BLACK)).owner(0).build())
        .build();
    List<RoundResult> r = Scoring.closeRound(s, 0, null);
    // closer (0): meld 15p - hand 5p + 50 bonus = 60
    assertThat(r.get(0).base()).isEqualTo(60);
  }
  @Test void closingWithJokerDoublesCloserOnly() {
    GameState s = GameStateBuilder.aGame().withPlayers("A", "B")
        .hand(0).etalat(0)
        .hand(1, p(7, Color.BLUE)).etalat(1)
        .melds(MeldBuilder.group(p(7,Color.RED), p(7,Color.BLUE), p(7,Color.BLACK)).owner(0).build())
        .build();
    Piece closingJoker = joker();
    List<RoundResult> r = Scoring.closeRound(s, 0, closingJoker);
    // closer: meld 15 - hand 0 + 50 = 65, doubled = 130
    assertThat(r.get(0).base()).isEqualTo(130);
  }
  @Test void jocDubluDoublesAll() {
    GameState s = GameStateBuilder.aGame().withPlayers("A", "B")
        .hand(0).etalat(0)
        .hand(1, p(7, Color.BLUE)).etalat(1)
        .melds(MeldBuilder.group(p(7,Color.RED), p(7,Color.BLUE), p(7,Color.BLACK)).owner(0).build())
        .doubleGame(true).build();
    List<RoundResult> r = Scoring.closeRound(s, 0, null);
    assertThat(r.get(0).base()).isEqualTo((15 + 50) * 2);   // 130
    assertThat(r.get(1).base()).isEqualTo((-5) * 2);        // -10
  }
}
```

- [ ] **Step 2: Run (FAIL)**

Run: `mvn -q test -Dtest=ScoringCloseRoundEtalatTest`

- [ ] **Step 3: Add `closeRound` to `Scoring.java`** (append):

```java
  public static List<RoundResult> closeRound(GameState state, int closerIdx, Piece lastDiscarded) {
    List<RoundResult> results = new java.util.ArrayList<>();
    boolean closedWithJoker = lastDiscarded != null && lastDiscarded.isJoker();

    for (int i = 0; i < state.players().size(); i++) {
      Player p = state.players().get(i);
      int pts = 0;

      for (Meld m : state.melds()) {
        for (Piece piece : m.pieces()) {
          Integer placedBy = m.placedBy().get(piece.id());
          int actualPlacer = placedBy != null ? placedBy : m.owner();
          if (actualPlacer == i) pts += finalPieceValue(piece);
        }
      }

      int handPts = p.hand().stream().mapToInt(Scoring::finalPieceValue).sum();
      pts -= handPts;

      if (!p.hasEtalat()) {
        pts = -100;
        if (p.calledAtu()) pts += 50;
      } else {
        if (i == closerIdx) pts += 50;
        if (p.calledAtu()) pts += 50;
      }

      if (state.mode() == Mode.ETALAT && i == closerIdx && closedWithJoker) {
        pts *= 2;
      }
      if (state.doubleGame()) {
        pts *= 2;
      }

      results.add(new RoundResult(i, p.name(), pts,
          (int) state.melds().stream().filter(m -> m.owner() == p.hashCode()).count(),
          p.hand().size()));
    }
    return results;
  }
```

- [ ] **Step 4: Run tests (PASS)** + **Step 5: Commit**

```bash
mvn -q test -Dtest=ScoringCloseRoundEtalatTest
git add src/main/java/com/remi/engine/rules/Scoring.java src/test/java/com/remi/engine/rules/ScoringCloseRoundEtalatTest.java
git commit -m "feat(engine): Scoring.closeRound — etalat mode with all bonuses"
```

---

### Task C6: `Scoring.closeRound` — tabla mode

**Files:**
- Modify: `src/main/java/com/remi/engine/rules/Scoring.java`
- Create: `src/test/java/com/remi/engine/rules/ScoringCloseRoundTablaTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.remi.engine.rules;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.remi.engine.testdata.Pieces.*;
import static org.assertj.core.api.Assertions.assertThat;

class ScoringCloseRoundTablaTest {
  @Test void tablaCloserGets250PerOther() {
    GameState s = GameStateBuilder.aGame().withPlayers("A","B","C").mode(Mode.TABLA).build();
    List<RoundResult> r = Scoring.closeRound(s, 0, null);
    assertThat(r.get(0).base()).isEqualTo(500);
    assertThat(r.get(1).base()).isEqualTo(-250);
    assertThat(r.get(2).base()).isEqualTo(-250);
  }
  @Test void tablaClosedWithJokerIs500() {
    GameState s = GameStateBuilder.aGame().withPlayers("A","B").mode(Mode.TABLA).build();
    List<RoundResult> r = Scoring.closeRound(s, 0, joker());
    assertThat(r.get(0).base()).isEqualTo(500);
    assertThat(r.get(1).base()).isEqualTo(-500);
  }
  @Test void tablaJocDubluDoublesAll() {
    GameState s = GameStateBuilder.aGame().withPlayers("A","B").mode(Mode.TABLA).doubleGame(true).build();
    List<RoundResult> r = Scoring.closeRound(s, 0, null);
    assertThat(r.get(0).base()).isEqualTo(500);   // 250 * 2
    assertThat(r.get(1).base()).isEqualTo(-500);
  }
}
```

- [ ] **Step 2: Run (FAIL — current closeRound returns etalat math for tabla)**

- [ ] **Step 3: Modify `closeRound` to branch on mode**

Replace `closeRound` body with:

```java
  public static List<RoundResult> closeRound(GameState state, int closerIdx, Piece lastDiscarded) {
    if (state.mode() == Mode.TABLA) {
      return closeRoundTabla(state, closerIdx, lastDiscarded);
    }
    return closeRoundEtalat(state, closerIdx, lastDiscarded);
  }

  private static List<RoundResult> closeRoundTabla(GameState state, int closerIdx, Piece lastDiscarded) {
    boolean closedWithJoker = lastDiscarded != null && lastDiscarded.isJoker();
    int baseAmount = closedWithJoker ? 500 : 250;
    int numOthers = state.players().size() - 1;
    List<RoundResult> results = new java.util.ArrayList<>();
    for (int i = 0; i < state.players().size(); i++) {
      Player p = state.players().get(i);
      int base = (i == closerIdx) ? baseAmount * numOthers : -baseAmount;
      if (state.doubleGame()) base *= 2;
      results.add(new RoundResult(i, p.name(), base, 0, p.hand().size()));
    }
    return results;
  }

  private static List<RoundResult> closeRoundEtalat(GameState state, int closerIdx, Piece lastDiscarded) {
    // (move existing etalat body here unchanged)
    ...
  }
```

Move the existing body (from C5) into `closeRoundEtalat` private method.

- [ ] **Step 4: Run all Scoring tests (all PASS)**

Run: `mvn -q test -Dtest=Scoring*`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/remi/engine/rules/Scoring.java src/test/java/com/remi/engine/rules/ScoringCloseRoundTablaTest.java
git commit -m "feat(engine): Scoring.closeRound — tabla mode with 250/500/double rules"
```

---

### Task C7: `Dealer` — deck creation, shuffle, deal

**Files:**
- Create: `src/main/java/com/remi/engine/rules/Dealer.java`
- Create: `src/test/java/com/remi/engine/rules/DealerTest.java`

- [ ] **Step 1: Write failing tests**

```java
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
    // Repeat until we observe both cases — with 1L..200L some seeds should produce each
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
```

- [ ] **Step 2: Run (FAIL)**

- [ ] **Step 3: Implement `Dealer.java`**

```java
package com.remi.engine.rules;

import com.remi.engine.domain.*;
import java.util.*;

public final class Dealer {
  private static final List<String> AI_NAMES = List.of("Ana", "Mihai", "Elena", "Radu", "Sorin");
  private Dealer() {}

  public static GameState deal(int numPlayers, Mode mode, Difficulty difficulty, long seed) {
    if (numPlayers < 2 || numPlayers > 6) {
      throw new IllegalArgumentException("numPlayers must be 2..6");
    }
    Random rng = new Random(seed);
    List<Piece> deck = makeDeck();
    Collections.shuffle(deck, rng);

    List<String> aiNames = new ArrayList<>(AI_NAMES);
    Collections.shuffle(aiNames, rng);

    List<Player> players = new ArrayList<>();
    List<List<Piece>> hands = new ArrayList<>();
    for (int i = 0; i < numPlayers; i++) hands.add(new ArrayList<>());
    for (int r = 0; r < 14; r++) {
      for (int i = 0; i < numPlayers; i++) {
        hands.get(i).add(deck.remove(deck.size() - 1));
      }
    }
    // First player gets one more piece
    hands.get(0).add(deck.remove(deck.size() - 1));

    Piece atu = deck.remove(deck.size() - 1);
    List<Piece> stock = deck;

    for (int i = 0; i < numPlayers; i++) {
      String name = (i == 0) ? "Tu" : aiNames.get(i - 1);
      boolean isBot = (i != 0);
      boolean calledAtu = hands.get(i).stream().anyMatch(p -> Piece.sameValue(p, atu));
      players.add(new Player(name, isBot, hands.get(i), false, calledAtu, false, null));
    }

    boolean doubleGame = atu.isJoker() || atu.num() == 1;

    List<Integer> totals = new ArrayList<>();
    for (int i = 0; i < numPlayers; i++) totals.add(0);

    return new GameState(
        UUID.randomUUID(), players, stock, List.of(), atu, List.of(),
        0, Phase.DISCARD, DrawSource.STOCK, 0, 1,
        mode, difficulty, doubleGame, false, totals, seed);
  }

  private static List<Piece> makeDeck() {
    List<Piece> deck = new ArrayList<>(106);
    int id = 0;
    for (Color c : List.of(Color.RED, Color.YELLOW, Color.BLUE, Color.BLACK)) {
      for (int set = 0; set < 2; set++) {
        for (int n = 1; n <= 13; n++) {
          deck.add(new Piece(id++, n, c, false));
        }
      }
    }
    deck.add(new Piece(id++, 0, Color.JOKER, true));
    deck.add(new Piece(id++, 0, Color.JOKER, true));
    return deck;
  }
}
```

**Note on initial state:** First player is dealt 15 pieces and `phase=DISCARD`, `drewFrom=STOCK` — they "have" their 15th piece as if they already drew, so their first action is `Discard`. Faithful to JS lines 1027 and 1196-1200.

- [ ] **Step 4: Run tests (PASS)** + **Step 5: Commit**

```bash
mvn -q test -Dtest=DealerTest
git add src/main/java/com/remi/engine/rules/Dealer.java src/test/java/com/remi/engine/rules/DealerTest.java
git commit -m "feat(engine): Dealer.deal — seeded shuffle, atu, double-game detection"
```

---

## Phase D — `GameEngine.apply`

This is the largest phase. Each action variant gets its own task with focused tests.

### Task D1: `GameEngine` skeleton + dispatch

**Files:**
- Create: `src/main/java/com/remi/engine/rules/GameEngine.java`
- Create: `src/test/java/com/remi/engine/rules/GameEngineDispatchTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.remi.engine.rules;

import com.remi.engine.domain.*;
import com.remi.engine.testdata.GameStateBuilder;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GameEngineDispatchTest {
  @Test void rejectsActionFromWrongPlayer() {
    GameState s = GameStateBuilder.aGame().withPlayers("A", "B").current(0).build();
    ActionResult r = GameEngine.apply(s, new Action.DrawFromStock(1));
    assertThat(r).isInstanceOf(ActionResult.Rejected.class);
    assertThat(((ActionResult.Rejected) r).code()).isEqualTo(RejectReason.NOT_YOUR_TURN);
  }
  @Test void rejectsActionWhenClosed() {
    GameState s = GameStateBuilder.aGame().withPlayers("A", "B").build();
    GameState closed = new GameState(s.id(), s.players(), s.stock(), s.discard(), s.atu(),
        s.melds(), s.current(), s.phase(), s.drewFrom(), s.turnTaken(), s.round(),
        s.mode(), s.difficulty(), s.doubleGame(), true, s.totals(), s.seed());
    ActionResult r = GameEngine.apply(closed, new Action.DrawFromStock(0));
    assertThat(r).isInstanceOf(ActionResult.Rejected.class);
    assertThat(((ActionResult.Rejected) r).code()).isEqualTo(RejectReason.GAME_CLOSED);
  }
}
```

- [ ] **Step 2: Run (FAIL)** — compilation error.

- [ ] **Step 3: Write skeleton `GameEngine.java`**

```java
package com.remi.engine.rules;

import com.remi.engine.domain.*;
import java.util.List;

public final class GameEngine {
  private GameEngine() {}

  public static ActionResult apply(GameState state, Action action) {
    if (state.closed()) return reject(RejectReason.GAME_CLOSED, "Runda este închisă.");
    if (action.playerIdx() != state.current()) return reject(RejectReason.NOT_YOUR_TURN, "Nu e rândul tău.");

    return switch (action) {
      case Action.DrawFromStock a   -> applyDrawFromStock(state, a);
      case Action.TakeDiscard a     -> applyTakeDiscard(state, a);
      case Action.Etalat a          -> applyEtalat(state, a);
      case Action.Layoff a          -> applyLayoff(state, a);
      case Action.Discard a         -> applyDiscard(state, a);
      case Action.ForceAutoAction a -> applyForceAuto(state, a);
    };
  }

  static ActionResult reject(RejectReason code, String msg) {
    return new ActionResult.Rejected(code, msg);
  }

  static ActionResult.Accepted accept(GameState s, DomainEvent... events) {
    return new ActionResult.Accepted(s, List.of(events));
  }

  // Stubs — implemented in subsequent tasks
  private static ActionResult applyDrawFromStock(GameState s, Action.DrawFromStock a) { throw new UnsupportedOperationException(); }
  private static ActionResult applyTakeDiscard(GameState s, Action.TakeDiscard a)     { throw new UnsupportedOperationException(); }
  private static ActionResult applyEtalat(GameState s, Action.Etalat a)               { throw new UnsupportedOperationException(); }
  private static ActionResult applyLayoff(GameState s, Action.Layoff a)               { throw new UnsupportedOperationException(); }
  private static ActionResult applyDiscard(GameState s, Action.Discard a)             { throw new UnsupportedOperationException(); }
  private static ActionResult applyForceAuto(GameState s, Action.ForceAutoAction a)   { throw new UnsupportedOperationException(); }
}
```

- [ ] **Step 4: Run (PASS)** + **Step 5: Commit**

```bash
mvn -q test -Dtest=GameEngineDispatchTest
git add src/main/java/com/remi/engine/rules/GameEngine.java src/test/java/com/remi/engine/rules/GameEngineDispatchTest.java
git commit -m "feat(engine): GameEngine dispatch + universal preconditions (closed, wrong turn)"
```

---

## Continuă în Part 3

Phase D rămas (D2-D9: implementări per acțiune + tranziții turn + close), Phase E (AI), F (property tests + golden), G (persistence), H (service), I (REST), J (E2E + coverage) — în `2026-05-16-stage1-game-engine-part3.md`.
