# Stage 1 — Game Engine Implementation Plan (Part 4: Golden + Properties + Persistence + Service + REST + E2E)

> **For agentic workers:** Final part. Same TDD discipline.

---

## Phase F — Golden playthrough + property tests

### Task F1: Golden full-game playthrough (Bots vs Bots, seed=42)

**Files:**
- Create: `src/test/java/com/remi/engine/ai/BotGoldenPlaythroughTest.java`

**Purpose:** Regression test — if any rule or AI change subtly breaks behavior, this test catches it. Played out once, results captured, frozen.

- [ ] **Step 1: Write the test** (initially asserts `closed=true` only; record actual totals after first run)

```java
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

    // After first run, capture exact totals here as a frozen regression check:
    // assertThat(s.totals()).containsExactly(<observed>, <observed>, <observed>);
  }
}
```

- [ ] **Step 2: Run; capture observed totals**

Run: `mvn -q test -Dtest=BotGoldenPlaythroughTest`
Expected: PASS. Read the actual totals from a debug print, then update the test:

Add to the test before final assertion:
```java
System.out.println("OBSERVED TOTALS: " + s.totals());
```
Run once, copy the values into:
```java
assertThat(s.totals()).containsExactly(/*p0*/ X, /*p1*/ Y, /*p2*/ Z);
```
Remove the System.out and re-run to confirm.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/remi/engine/ai/BotGoldenPlaythroughTest.java
git commit -m "test(ai): golden full-game playthrough seed=42 with frozen totals"
```

---

### Task F2: Property-based tests (jqwik)

**Files:**
- Create: `src/test/java/com/remi/engine/property/EngineProperties.java`

- [ ] **Step 1: Write properties**

```java
package com.remi.engine.property;

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
```

- [ ] **Step 2: Run** + **Step 3: Commit**

```bash
mvn -q test -Dtest=EngineProperties
git add src/test/java/com/remi/engine/property/EngineProperties.java
git commit -m "test(engine): jqwik properties — dealer validity + bot never rejects"
```

---

## Phase G — Persistence

### Task G1: Jackson config for sealed interfaces

**Files:**
- Create: `src/main/java/com/remi/config/JacksonConfig.java`

Sealed interfaces with records need `@JsonTypeInfo` to round-trip. We use class-name-based polymorphism on the test surface (deterministic across versions if we never rename).

- [ ] **Step 1: Write `JacksonConfig.java`**

```java
package com.remi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.remi.engine.domain.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {
  @Bean
  @Primary
  public ObjectMapper objectMapper() {
    return JsonMapper.builder()
        .findAndAddModules()
        .addMixIn(Action.class, ActionMixIn.class)
        .addMixIn(ActionResult.class, ActionResultMixIn.class)
        .addMixIn(DomainEvent.class, DomainEventMixIn.class)
        .build();
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = Action.DrawFromStock.class, name = "DRAW_FROM_STOCK"),
    @JsonSubTypes.Type(value = Action.TakeDiscard.class, name = "TAKE_DISCARD"),
    @JsonSubTypes.Type(value = Action.Etalat.class, name = "ETALAT"),
    @JsonSubTypes.Type(value = Action.Layoff.class, name = "LAYOFF"),
    @JsonSubTypes.Type(value = Action.Discard.class, name = "DISCARD"),
    @JsonSubTypes.Type(value = Action.ForceAutoAction.class, name = "FORCE_AUTO")
  })
  abstract static class ActionMixIn {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = ActionResult.Accepted.class, name = "ACCEPTED"),
    @JsonSubTypes.Type(value = ActionResult.Rejected.class, name = "REJECTED")
  })
  abstract static class ActionResultMixIn {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = DomainEvent.TurnStarted.class, name = "TURN_STARTED"),
    @JsonSubTypes.Type(value = DomainEvent.CardDrawn.class, name = "CARD_DRAWN"),
    @JsonSubTypes.Type(value = DomainEvent.DiscardTaken.class, name = "DISCARD_TAKEN"),
    @JsonSubTypes.Type(value = DomainEvent.PieceDiscarded.class, name = "PIECE_DISCARDED"),
    @JsonSubTypes.Type(value = DomainEvent.PlayerEtalat.class, name = "PLAYER_ETALAT"),
    @JsonSubTypes.Type(value = DomainEvent.LayoffPlayed.class, name = "LAYOFF_PLAYED"),
    @JsonSubTypes.Type(value = DomainEvent.RoundClosed.class, name = "ROUND_CLOSED"),
    @JsonSubTypes.Type(value = DomainEvent.StockExhausted.class, name = "STOCK_EXHAUSTED")
  })
  abstract static class DomainEventMixIn {}
}
```

- [ ] **Step 2: Compile** + **Step 3: Commit**

```bash
mvn -q compile
git add src/main/java/com/remi/config/JacksonConfig.java
git commit -m "feat(config): Jackson polymorphic mixins for Action / ActionResult / DomainEvent"
```

---

### Task G2: `GameEntity` + `GameRepository`

**Files:**
- Create: `src/main/java/com/remi/persistence/GameEntity.java`
- Create: `src/main/java/com/remi/persistence/GameRepository.java`

- [ ] **Step 1: Write `GameEntity.java`**

```java
package com.remi.persistence;

import com.remi.engine.domain.GameState;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "games")
public class GameEntity {
  @Id
  private UUID id;

  @Type(JsonBinaryType.class)
  @Column(name = "state", columnDefinition = "jsonb", nullable = false)
  private GameState state;

  @Version
  @Column(nullable = false)
  private Long version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected GameEntity() {}
  public GameEntity(UUID id, GameState state) { this.id = id; this.state = state; }

  public UUID getId() { return id; }
  public GameState getState() { return state; }
  public void setState(GameState state) { this.state = state; }
  public Long getVersion() { return version; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 2: Write `GameRepository.java`**

```java
package com.remi.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface GameRepository extends JpaRepository<GameEntity, UUID> {}
```

- [ ] **Step 3: Compile + Commit**

```bash
mvn -q compile
git add src/main/java/com/remi/persistence/
git commit -m "feat(persistence): GameEntity (JSONB state) + GameRepository"
```

---

### Task G3: Persistence integration test (Testcontainers)

**Files:**
- Create: `src/test/java/com/remi/persistence/GameRepositoryIT.java`

- [ ] **Step 1: Write test**

```java
package com.remi.persistence;

import com.remi.engine.domain.GameState;
import com.remi.engine.rules.Dealer;
import com.remi.engine.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.remi.config.JacksonConfig;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JacksonConfig.class)
class GameRepositoryIT {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired GameRepository repo;

  @Test
  void roundTripsGameStateThroughJsonb() {
    GameState s = Dealer.deal(3, Mode.ETALAT, Difficulty.MED, 42L);
    GameEntity e = new GameEntity(s.id(), s);
    repo.save(e);
    GameEntity loaded = repo.findById(s.id()).orElseThrow();
    assertThat(loaded.getState()).isEqualTo(s);
  }
}
```

- [ ] **Step 2: Run** (requires Docker)

```bash
mvn -q test -Dtest=GameRepositoryIT
```

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/remi/persistence/GameRepositoryIT.java
git commit -m "test(persistence): Testcontainers IT — JSONB round-trip"
```

---

## Phase H — Service

### Task H1: Custom exceptions

**Files:**
- Create: `src/main/java/com/remi/service/GameRuleException.java`
- Create: `src/main/java/com/remi/service/GameNotFoundException.java`
- Create: `src/main/java/com/remi/service/IllegalEngineStateException.java`

- [ ] **Step 1: Write all three**

```java
package com.remi.service;
import com.remi.engine.domain.RejectReason;
public class GameRuleException extends RuntimeException {
  private final RejectReason code;
  public GameRuleException(RejectReason code, String msg) { super(msg); this.code = code; }
  public RejectReason getCode() { return code; }
}
```
```java
package com.remi.service;
import java.util.UUID;
public class GameNotFoundException extends RuntimeException {
  public GameNotFoundException(UUID id) { super("Joc inexistent: " + id); }
}
```
```java
package com.remi.service;
public class IllegalEngineStateException extends RuntimeException {
  public IllegalEngineStateException(String msg) { super(msg); }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/remi/service/*.java
git commit -m "feat(service): custom exceptions for rule violations, not-found, engine bugs"
```

---

### Task H2: `GameService` — `create`, `applyAction`, `runBotsUntilHuman`, `get`

**Files:**
- Create: `src/main/java/com/remi/service/GameService.java`
- Create: `src/test/java/com/remi/service/GameServiceIT.java`

- [ ] **Step 1: Write failing test**

```java
package com.remi.service;

import com.remi.config.JacksonConfig;
import com.remi.engine.domain.*;
import com.remi.persistence.GameRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class GameServiceIT {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired GameService service;
  @Autowired GameRepository repo;

  @Test
  void createReturnsValidGame() {
    GameState s = service.create(3, Mode.ETALAT, Difficulty.MED, 42L);
    assertThat(s.players()).hasSize(3);
    assertThat(repo.findById(s.id())).isPresent();
  }

  @Test
  void applyActionPersistsNewState() {
    GameState s = service.create(2, Mode.ETALAT, Difficulty.MED, 42L);
    // Initial state: phase=DISCARD for player 0 (with 15 cards). Discard the first card.
    int firstPieceId = s.players().get(0).hand().get(0).id();
    GameState after = service.applyAction(s.id(), new Action.Discard(0, firstPieceId));
    assertThat(after.players().get(0).hand()).hasSize(14);
  }

  @Test
  void applyActionWithRuleViolationThrowsAndDoesNotMutate() {
    GameState s = service.create(2, Mode.ETALAT, Difficulty.MED, 42L);
    assertThatThrownBy(() -> service.applyAction(s.id(), new Action.Discard(0, 99999)))
        .isInstanceOf(GameRuleException.class);
    GameState reloaded = service.get(s.id());
    assertThat(reloaded.players().get(0).hand()).hasSize(15);  // unchanged
  }

  @Test
  void getThrowsOnMissing() {
    assertThatThrownBy(() -> service.get(UUID.randomUUID()))
        .isInstanceOf(GameNotFoundException.class);
  }
}
```

- [ ] **Step 2: Run (FAIL)**

- [ ] **Step 3: Implement `GameService.java`**

```java
package com.remi.service;

import com.remi.engine.ai.Bot;
import com.remi.engine.domain.*;
import com.remi.engine.rules.Dealer;
import com.remi.engine.rules.GameEngine;
import com.remi.persistence.GameEntity;
import com.remi.persistence.GameRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class GameService {
  private static final Logger log = LoggerFactory.getLogger(GameService.class);
  private static final int MAX_BOT_STEPS = 100;

  private final GameRepository repo;
  public GameService(GameRepository repo) { this.repo = repo; }

  @Transactional
  public GameState create(int numPlayers, Mode mode, Difficulty difficulty, Long seed) {
    long actualSeed = (seed != null) ? seed : System.nanoTime();
    GameState s = Dealer.deal(numPlayers, mode, difficulty, actualSeed);
    repo.save(new GameEntity(s.id(), s));
    log.info("Created game {} numPlayers={} mode={} seed={}", s.id(), numPlayers, mode, actualSeed);
    return s;
  }

  @Transactional(readOnly = true)
  public GameState get(UUID gameId) {
    return repo.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId)).getState();
  }

  @Transactional
  public GameState applyAction(UUID gameId, Action action) {
    GameEntity entity = repo.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
    ActionResult result = GameEngine.apply(entity.getState(), action);
    switch (result) {
      case ActionResult.Rejected r -> {
        log.warn("Rejected action on game {} code={}", gameId, r.code());
        throw new GameRuleException(r.code(), r.message());
      }
      case ActionResult.Accepted a -> {
        entity.setState(a.newState());
        repo.save(entity);
        log.debug("Action {} accepted on game {}", action.getClass().getSimpleName(), gameId);
        return a.newState();
      }
    }
  }

  @Transactional
  public GameState runBotsUntilHuman(UUID gameId) {
    GameEntity entity = repo.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
    GameState state = entity.getState();
    int steps = 0;
    while (!state.closed() && state.players().get(state.current()).isBot()) {
      if (steps++ >= MAX_BOT_STEPS) {
        throw new IllegalEngineStateException("Bot loop exceeded " + MAX_BOT_STEPS + " steps on game " + gameId);
      }
      Action a = Bot.decide(state, state.current());
      ActionResult r = GameEngine.apply(state, a);
      if (r instanceof ActionResult.Rejected rej) {
        throw new IllegalEngineStateException("Bot proposed rejected action: " + rej.code() + " — " + rej.message());
      }
      state = ((ActionResult.Accepted) r).newState();
    }
    entity.setState(state);
    repo.save(entity);
    return state;
  }
}
```

- [ ] **Step 4: Run (PASS)** + **Step 5: Commit**

```bash
mvn -q test -Dtest=GameServiceIT
git add src/main/java/com/remi/service/GameService.java src/test/java/com/remi/service/GameServiceIT.java
git commit -m "feat(service): GameService — create, applyAction, runBots, get"
```

---

## Phase I — REST API

### Task I1: DTOs + `GameView` projection

**Files:**
- Create: `src/main/java/com/remi/api/CreateGameRequest.java`
- Create: `src/main/java/com/remi/api/ApplyResponse.java`
- Create: `src/main/java/com/remi/api/GameView.java`
- Create: `src/main/java/com/remi/api/ApiError.java`

- [ ] **Step 1: Write all four DTOs**

```java
package com.remi.api;
import com.remi.engine.domain.*;
import jakarta.validation.constraints.*;
public record CreateGameRequest(
    @Min(2) @Max(6) int numPlayers,
    @NotNull Mode mode,
    @NotNull Difficulty difficulty,
    Long seed
) {}
```
```java
package com.remi.api;
import com.remi.engine.domain.DomainEvent;
import java.util.List;
public record ApplyResponse(GameView view, List<DomainEvent> events) {}
```
```java
package com.remi.api;
import com.remi.engine.domain.*;
import java.util.*;
public record GameView(
    UUID id, List<PlayerView> players, int stockCount, List<Piece> discard, Piece atu,
    List<Meld> melds, int current, Phase phase, DrawSource drewFrom, int turnTaken,
    int round, Mode mode, Difficulty difficulty, boolean doubleGame, boolean closed,
    List<Integer> totals
) {
  public record PlayerView(String name, boolean isBot, boolean hasEtalat,
                           boolean calledAtu, boolean announced, Integer mustUsePieceId,
                           List<Piece> hand, int handCount) {}
  public static GameView of(GameState s, int viewerIdx) {
    List<PlayerView> pvs = new ArrayList<>();
    for (int i = 0; i < s.players().size(); i++) {
      Player p = s.players().get(i);
      List<Piece> visibleHand = (i == viewerIdx) ? p.hand() : List.of();
      pvs.add(new PlayerView(p.name(), p.isBot(), p.hasEtalat(), p.calledAtu(),
          p.announced(), p.mustUsePieceId(), visibleHand, p.hand().size()));
    }
    return new GameView(s.id(), pvs, s.stock().size(), s.discard(), s.atu(), s.melds(),
        s.current(), s.phase(), s.drewFrom(), s.turnTaken(), s.round(), s.mode(),
        s.difficulty(), s.doubleGame(), s.closed(), s.totals());
  }
}
```
```java
package com.remi.api;
public record ApiError(String code, String message) {}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/remi/api/
git commit -m "feat(api): DTOs CreateGameRequest, ApplyResponse, GameView, ApiError"
```

---

### Task I2: Global exception handler

**Files:**
- Create: `src/main/java/com/remi/api/ApiExceptionHandler.java`

- [ ] **Step 1: Write handler**

```java
package com.remi.api;

import com.remi.service.GameNotFoundException;
import com.remi.service.GameRuleException;
import com.remi.service.IllegalEngineStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(GameRuleException.class)
  public ResponseEntity<ApiError> rule(GameRuleException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(e.getCode().name(), e.getMessage()));
  }

  @ExceptionHandler(GameNotFoundException.class)
  public ResponseEntity<ApiError> notFound(GameNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("GAME_NOT_FOUND", e.getMessage()));
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ApiError> race(OptimisticLockingFailureException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("GAME_VERSION_CONFLICT", "Joc actualizat între timp."));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("INVALID_REQUEST", e.getMessage()));
  }

  @ExceptionHandler(IllegalEngineStateException.class)
  public ResponseEntity<ApiError> engineBug(IllegalEngineStateException e) {
    log.error("Engine state bug", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("ENGINE_ERROR", "Eroare internă engine."));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> unexpected(Exception e) {
    log.error("Unexpected", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("INTERNAL_ERROR", "Eroare neașteptată."));
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/remi/api/ApiExceptionHandler.java
git commit -m "feat(api): global exception handler with HTTP status mapping"
```

---

### Task I3: `GameController`

**Files:**
- Create: `src/main/java/com/remi/api/GameController.java`

- [ ] **Step 1: Write controller**

```java
package com.remi.api;

import com.remi.engine.domain.Action;
import com.remi.engine.domain.GameState;
import com.remi.service.GameService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/dev/games")
public class GameController {
  private final GameService service;
  public GameController(GameService service) { this.service = service; }

  @PostMapping
  public ResponseEntity<GameView> create(@Valid @RequestBody CreateGameRequest req) {
    GameState s = service.create(req.numPlayers(), req.mode(), req.difficulty(), req.seed());
    return ResponseEntity.created(URI.create("/api/dev/games/" + s.id())).body(GameView.of(s, 0));
  }

  @GetMapping("/{id}")
  public GameView get(@PathVariable UUID id, @RequestParam(defaultValue = "0") int viewer) {
    return GameView.of(service.get(id), viewer);
  }

  @PostMapping("/{id}/actions")
  public GameView apply(@PathVariable UUID id, @RequestBody Action action) {
    GameState s = service.applyAction(id, action);
    return GameView.of(s, action.playerIdx());
  }

  @PostMapping("/{id}/bot")
  public GameView runBots(@PathVariable UUID id, @RequestParam(defaultValue = "0") int viewer) {
    GameState s = service.runBotsUntilHuman(id);
    return GameView.of(s, viewer);
  }
}
```

- [ ] **Step 2: Compile + Commit**

```bash
mvn -q compile
git add src/main/java/com/remi/api/GameController.java
git commit -m "feat(api): GameController — POST /games, GET /games/{id}, /actions, /bot"
```

---

## Phase J — E2E + final coverage gate

### Task J1: End-to-end REST test

**Files:**
- Create: `src/test/java/com/remi/api/GameApiE2ETest.java`

- [ ] **Step 1: Write E2E test**

```java
package com.remi.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class GameApiE2ETest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper om;

  @Test
  void createGameThenRunBotsThenClose() throws Exception {
    String body = """
        {"numPlayers":3,"mode":"ETALAT","difficulty":"MED","seed":42}
        """;
    String resp = mvc.perform(post("/api/dev/games").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    JsonNode view = om.readTree(resp);
    String id = view.get("id").asText();

    // Discard first piece (player 0 starts in DISCARD phase with 15 cards)
    int firstPieceId = view.get("players").get(0).get("hand").get(0).get("id").asInt();
    String discardBody = String.format("{\"type\":\"DISCARD\",\"playerIdx\":0,\"pieceId\":%d}", firstPieceId);
    mvc.perform(post("/api/dev/games/" + id + "/actions").contentType(MediaType.APPLICATION_JSON).content(discardBody))
        .andExpect(status().isOk());

    // Let bots play until they finish or it's player 0 again
    String afterBots = mvc.perform(post("/api/dev/games/" + id + "/bot"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    JsonNode afterBotsView = om.readTree(afterBots);
    // Either game closed, or it's player 0's turn
    boolean closed = afterBotsView.get("closed").asBoolean();
    int current = afterBotsView.get("current").asInt();
    assertThat(closed || current == 0).isTrue();
  }

  @Test
  void invalidActionReturns400() throws Exception {
    String body = """
        {"numPlayers":2,"mode":"ETALAT","difficulty":"MED","seed":42}
        """;
    String resp = mvc.perform(post("/api/dev/games").contentType(MediaType.APPLICATION_JSON).content(body))
        .andReturn().getResponse().getContentAsString();
    String id = om.readTree(resp).get("id").asText();

    String invalidAction = "{\"type\":\"DISCARD\",\"playerIdx\":0,\"pieceId\":999999}";
    mvc.perform(post("/api/dev/games/" + id + "/actions").contentType(MediaType.APPLICATION_JSON).content(invalidAction))
        .andExpect(status().isBadRequest());
  }
}
```

- [ ] **Step 2: Run** (requires Docker)

```bash
mvn -q test -Dtest=GameApiE2ETest
```

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/remi/api/GameApiE2ETest.java
git commit -m "test(api): E2E — full game cycle + invalid action via MockMvc"
```

---

### Task J2: Final verify (all tests + coverage gate)

- [ ] **Step 1: Run full build**

Run: `mvn -q verify`
Expected: all tests pass, JaCoCo coverage report generated, coverage gate enforces ≥90% on `com.remi.engine.*`.

- [ ] **Step 2: If coverage gate fails, identify gaps and add targeted tests, then re-run**

Open: `target/site/jacoco/index.html`
Add tests for uncovered branches in engine until ≥90%.

- [ ] **Step 3: Final commit (only if test additions were needed)**

```bash
git add src/test/
git commit -m "test(engine): close coverage gaps to satisfy 90% gate"
```

---

### Task J3: README with run instructions

**Files:**
- Create: `README.md`

- [ ] **Step 1: Write `README.md`**

```markdown
# Remi Backend — Stage 1: Game Engine

Pure-functional Java game engine for Romanian Remi (tile variant), wrapped by Spring Boot service with PostgreSQL JSONB persistence and dev REST endpoints.

## Prerequisites
- Java 21
- Maven 3.9+
- Docker (for tests + local Postgres)

## Run locally
```bash
# Start Postgres
docker run -d --name remi-pg -p 5432:5432 \
  -e POSTGRES_USER=remi -e POSTGRES_PASSWORD=remi -e POSTGRES_DB=remi \
  postgres:16-alpine

mvn spring-boot:run
```

## Try the API
```bash
# Create a game
curl -X POST http://localhost:8080/api/dev/games \
  -H 'Content-Type: application/json' \
  -d '{"numPlayers":3,"mode":"ETALAT","difficulty":"MED","seed":42}'

# Get state
curl http://localhost:8080/api/dev/games/<id>

# Discard a piece
curl -X POST http://localhost:8080/api/dev/games/<id>/actions \
  -H 'Content-Type: application/json' \
  -d '{"type":"DISCARD","playerIdx":0,"pieceId":<pid>}'

# Let bots play
curl -X POST http://localhost:8080/api/dev/games/<id>/bot
```

## Tests
```bash
mvn verify   # all tests + coverage gate (≥90% engine)
```

## Architecture
See `docs/superpowers/specs/2026-05-16-stage1-game-engine-design.md`.
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: README with run + API examples for Stage 1"
```

---

## Stage 1 is complete

All phases (A–J) done. Engine + AI + persistence + REST + tests + coverage gate. Next stage (Auth + users) gets its own spec via `superpowers:brainstorming`.
