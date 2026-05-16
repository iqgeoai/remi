# Stage 1 — Game Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a pure-functional Java game engine for Remi (Romanian Rummy with tiles), wrapped by a Spring service with PostgreSQL JSONB snapshot persistence, exposed via dev-only REST endpoints. Faithful port of `assets/remi.html` rules + AI.

**Architecture:** `com.remi.engine.*` is pure functional (records, `apply(state, action) → ActionResult`), zero Spring/JPA deps. `com.remi.service` wraps it with `@Service` that loads/saves a single `GameEntity(id, state JSONB, version)`. `com.remi.api` exposes dev REST. Spec: `docs/superpowers/specs/2026-05-16-stage1-game-engine-design.md`.

**Tech Stack:** Java 21, Spring Boot 3.3+, Maven, PostgreSQL 16, Flyway, Jackson, JUnit 5, AssertJ, jqwik (property-based), Testcontainers, JaCoCo, PIT.

**Source of truth for rules:** `assets/remi.html` — port faithfully. When in doubt, the JS implementation wins.

---

## File Structure

```
pom.xml
src/main/java/com/remi/
  RemiApplication.java
  engine/
    domain/
      Color.java, MeldType.java, Phase.java, Mode.java, DrawSource.java, Difficulty.java   (enums)
      Piece.java, Player.java, Meld.java, GameState.java                                   (state records)
      Action.java                                                                          (sealed interface + 6 variants)
      MeldProposal.java, LayoffProposal.java                                               (action payload records)
      ActionResult.java, RejectReason.java                                                 (result types)
      DomainEvent.java, RoundResult.java                                                   (events/results)
    rules/
      MeldValidator.java     (isValid)
      Scoring.java           (finalPieceValue, firstMeldPieceValue, inferJokerNumber, closeRound)
      Dealer.java            (deal — seeded)
      GameEngine.java        (apply: dispatch + state transitions)
    ai/
      MeldFinder.java        (findAnyMelds, findFirstMeldSet, findLayoffs)
      Bot.java               (decide → Action)
  persistence/
    GameEntity.java
    GameRepository.java
  service/
    GameService.java
    GameRuleException.java, GameNotFoundException.java, IllegalEngineStateException.java
  api/
    GameController.java
    ApiExceptionHandler.java
    ApiError.java, CreateGameRequest.java, ApplyResponse.java, GameView.java
  config/
    JacksonConfig.java       (sealed-interface polymorphic deserialization)
src/main/resources/
  application.yml
  db/migration/V1__init_games.sql
src/test/java/com/remi/
  engine/testdata/
    PieceBuilder.java, MeldBuilder.java, GameStateBuilder.java
  engine/rules/
    MeldValidatorTest.java, ScoringTest.java, DealerTest.java
    GameEngineDrawTest.java, GameEngineDiscardTest.java, GameEngineEtalatTest.java,
    GameEngineLayoffTest.java, GameEngineTakeDiscardTest.java, GameEngineForceAutoTest.java,
    GameEngineRoundCloseTest.java, GameEngineAcceptanceTest.java
  engine/ai/
    MeldFinderTest.java, BotTest.java, BotGoldenPlaythroughTest.java
  engine/property/
    EngineProperties.java   (jqwik @Property tests)
  persistence/
    GameRepositoryIT.java
  service/
    GameServiceIT.java
  api/
    GameApiE2ETest.java
```

---

## Phase A — Project Scaffold

### Task A1: Maven `pom.xml` with all dependencies

**Files:**
- Create: `pom.xml`

- [ ] **Step 1: Write `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.5</version>
    <relativePath/>
  </parent>
  <groupId>com.remi</groupId>
  <artifactId>remi-backend</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <properties>
    <java.version>21</java.version>
    <jqwik.version>1.9.1</jqwik.version>
    <testcontainers.version>1.20.3</testcontainers.version>
  </properties>
  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
    <dependency><groupId>io.hypersistence</groupId><artifactId>hypersistence-utils-hibernate-63</artifactId><version>3.8.3</version></dependency>

    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.assertj</groupId><artifactId>assertj-core</artifactId><scope>test</scope></dependency>
    <dependency><groupId>net.jqwik</groupId><artifactId>jqwik</artifactId><version>${jqwik.version}</version><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>postgresql</artifactId><version>${testcontainers.version}</version><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId><version>${testcontainers.version}</version><scope>test</scope></dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
      <plugin>
        <groupId>org.jacoco</groupId>
        <artifactId>jacoco-maven-plugin</artifactId>
        <version>0.8.12</version>
        <executions>
          <execution><goals><goal>prepare-agent</goal></goals></execution>
          <execution><id>report</id><phase>test</phase><goals><goal>report</goal></goals></execution>
          <execution>
            <id>check</id><phase>verify</phase><goals><goal>check</goal></goals>
            <configuration>
              <rules>
                <rule>
                  <element>PACKAGE</element>
                  <includes><include>com.remi.engine.*</include></includes>
                  <limits><limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.90</minimum></limit></limits>
                </rule>
              </rules>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Verify Maven resolves**

Run: `mvn -q -DskipTests dependency:resolve`
Expected: BUILD SUCCESS, downloads dependencies.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: scaffold Maven pom with Spring Boot, Postgres, Flyway, JaCoCo"
```

---

### Task A2: Spring Boot application + config

**Files:**
- Create: `src/main/java/com/remi/RemiApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/application-test.yml`

- [ ] **Step 1: Write `RemiApplication.java`**

```java
package com.remi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RemiApplication {
  public static void main(String[] args) {
    SpringApplication.run(RemiApplication.class, args);
  }
}
```

- [ ] **Step 2: Write `application.yml`**

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/remi}
    username: ${DB_USER:remi}
    password: ${DB_PASS:remi}
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.jdbc.lob.non_contextual_creation: true
  flyway:
    enabled: true
    locations: classpath:db/migration
server:
  port: 8080
logging:
  level:
    com.remi: INFO
```

- [ ] **Step 3: Write `application-test.yml`**

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
logging:
  level:
    com.remi: DEBUG
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/remi/RemiApplication.java src/main/resources/application.yml src/main/resources/application-test.yml
git commit -m "feat: Spring Boot app entrypoint + datasource config"
```

---

### Task A3: Flyway migration V1 (games table)

**Files:**
- Create: `src/main/resources/db/migration/V1__init_games.sql`

- [ ] **Step 1: Write migration**

```sql
CREATE TABLE games (
  id          UUID PRIMARY KEY,
  state       JSONB NOT NULL,
  version     BIGINT NOT NULL DEFAULT 0,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX games_updated_at_idx ON games(updated_at);
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/db/migration/V1__init_games.sql
git commit -m "feat: Flyway V1 — games table with JSONB state"
```

---

## Phase B — Domain Records

### Task B1: Enums

**Files:**
- Create: `src/main/java/com/remi/engine/domain/Color.java`
- Create: `src/main/java/com/remi/engine/domain/MeldType.java`
- Create: `src/main/java/com/remi/engine/domain/Phase.java`
- Create: `src/main/java/com/remi/engine/domain/Mode.java`
- Create: `src/main/java/com/remi/engine/domain/DrawSource.java`
- Create: `src/main/java/com/remi/engine/domain/Difficulty.java`

- [ ] **Step 1: Write all enums** (each in its own file under `com.remi.engine.domain`)

```java
package com.remi.engine.domain;
public enum Color { RED, YELLOW, BLUE, BLACK, JOKER }
```
```java
public enum MeldType { GROUP, SUITE }
```
```java
public enum Phase { DRAW, ACTION, DISCARD }
```
```java
public enum Mode { ETALAT, TABLA }
```
```java
public enum DrawSource { STOCK, DISCARD }
```
```java
public enum Difficulty { EASY, MED, HARD }
```

- [ ] **Step 2: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/remi/engine/domain/
git commit -m "feat(engine): add domain enums"
```

---

### Task B2: `Piece` record + equality test

**Files:**
- Create: `src/main/java/com/remi/engine/domain/Piece.java`
- Create: `src/test/java/com/remi/engine/domain/PieceTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.remi.engine.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PieceTest {
  @Test void sameValueIgnoresId() {
    Piece a = new Piece(1, 5, Color.RED, false);
    Piece b = new Piece(99, 5, Color.RED, false);
    assertThat(Piece.sameValue(a, b)).isTrue();
  }
  @Test void sameValueFalseOnDifferentNum() {
    assertThat(Piece.sameValue(new Piece(1, 5, Color.RED, false), new Piece(2, 6, Color.RED, false))).isFalse();
  }
  @Test void twoJokersAreSameValue() {
    assertThat(Piece.sameValue(new Piece(1, 0, Color.JOKER, true), new Piece(2, 0, Color.JOKER, true))).isTrue();
  }
  @Test void jokerVsNonJokerNotSame() {
    assertThat(Piece.sameValue(new Piece(1, 0, Color.JOKER, true), new Piece(2, 5, Color.RED, false))).isFalse();
  }
}
```

- [ ] **Step 2: Run test (should FAIL — class missing)**

Run: `mvn -q test -Dtest=PieceTest`
Expected: COMPILATION FAILURE.

- [ ] **Step 3: Write `Piece` record**

```java
package com.remi.engine.domain;

public record Piece(int id, int num, Color color, boolean isJoker) {
  public static boolean sameValue(Piece a, Piece b) {
    if (a.isJoker && b.isJoker) return true;
    if (a.isJoker || b.isJoker) return false;
    return a.num == b.num && a.color == b.color;
  }
}
```

- [ ] **Step 4: Run test (should PASS)**

Run: `mvn -q test -Dtest=PieceTest`
Expected: BUILD SUCCESS, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/remi/engine/domain/Piece.java src/test/java/com/remi/engine/domain/PieceTest.java
git commit -m "feat(engine): add Piece record with sameValue"
```

---

### Task B3: `Meld`, `Player`, `GameState` records (no tests yet — covered by builders + engine tests)

**Files:**
- Create: `src/main/java/com/remi/engine/domain/Meld.java`
- Create: `src/main/java/com/remi/engine/domain/Player.java`
- Create: `src/main/java/com/remi/engine/domain/GameState.java`

- [ ] **Step 1: Write `Meld.java`**

```java
package com.remi.engine.domain;

import java.util.List;
import java.util.Map;

public record Meld(
    int owner,
    MeldType type,
    List<Piece> pieces,
    Map<Integer, Integer> placedBy  // pieceId -> playerIdx (for layoffs onto others' melds)
) {
  public Meld {
    pieces = List.copyOf(pieces);
    placedBy = Map.copyOf(placedBy);
  }
}
```

- [ ] **Step 2: Write `Player.java`**

```java
package com.remi.engine.domain;

import java.util.List;

public record Player(
    String name,
    boolean isBot,
    List<Piece> hand,
    boolean hasEtalat,
    boolean calledAtu,
    boolean announced,
    Integer mustUsePieceId  // null if none
) {
  public Player {
    hand = List.copyOf(hand);
  }
}
```

- [ ] **Step 3: Write `GameState.java`**

```java
package com.remi.engine.domain;

import java.util.List;
import java.util.UUID;

public record GameState(
    UUID id,
    List<Player> players,
    List<Piece> stock,
    List<Piece> discard,
    Piece atu,
    List<Meld> melds,
    int current,
    Phase phase,
    DrawSource drewFrom,        // null if not drawn yet this turn
    int turnTaken,
    int round,
    Mode mode,
    Difficulty difficulty,
    boolean doubleGame,
    boolean closed,
    List<Integer> totals,
    long seed
) {
  public GameState {
    players = List.copyOf(players);
    stock = List.copyOf(stock);
    discard = List.copyOf(discard);
    melds = List.copyOf(melds);
    totals = List.copyOf(totals);
  }
}
```

- [ ] **Step 4: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/remi/engine/domain/Meld.java src/main/java/com/remi/engine/domain/Player.java src/main/java/com/remi/engine/domain/GameState.java
git commit -m "feat(engine): add Meld, Player, GameState records"
```

---

### Task B4: `Action` sealed interface + `MeldProposal`, `LayoffProposal`

**Files:**
- Create: `src/main/java/com/remi/engine/domain/Action.java`
- Create: `src/main/java/com/remi/engine/domain/MeldProposal.java`
- Create: `src/main/java/com/remi/engine/domain/LayoffProposal.java`

- [ ] **Step 1: Write `MeldProposal.java` and `LayoffProposal.java`**

```java
package com.remi.engine.domain;
import java.util.List;
public record MeldProposal(MeldType type, List<Integer> pieceIds) {
  public MeldProposal { pieceIds = List.copyOf(pieceIds); }
}
```
```java
package com.remi.engine.domain;
public record LayoffProposal(int pieceId, int meldIdx) {}
```

- [ ] **Step 2: Write `Action.java`**

```java
package com.remi.engine.domain;

import java.util.List;

public sealed interface Action {
  int playerIdx();

  record DrawFromStock(int playerIdx) implements Action {}
  record TakeDiscard(int playerIdx, int discardIdx) implements Action {}
  record Etalat(int playerIdx, List<MeldProposal> melds) implements Action {
    public Etalat { melds = List.copyOf(melds); }
  }
  record Layoff(int playerIdx, List<LayoffProposal> layoffs) implements Action {
    public Layoff { layoffs = List.copyOf(layoffs); }
  }
  record Discard(int playerIdx, int pieceId) implements Action {}
  record ForceAutoAction(int playerIdx) implements Action {}
}
```

- [ ] **Step 3: Compile + Commit**

```bash
mvn -q compile
git add src/main/java/com/remi/engine/domain/Action.java src/main/java/com/remi/engine/domain/MeldProposal.java src/main/java/com/remi/engine/domain/LayoffProposal.java
git commit -m "feat(engine): add Action sealed interface + payload records"
```

---

### Task B5: `ActionResult`, `RejectReason`, `DomainEvent`, `RoundResult`

**Files:**
- Create: `src/main/java/com/remi/engine/domain/RejectReason.java`
- Create: `src/main/java/com/remi/engine/domain/ActionResult.java`
- Create: `src/main/java/com/remi/engine/domain/DomainEvent.java`
- Create: `src/main/java/com/remi/engine/domain/RoundResult.java`

- [ ] **Step 1: Write `RejectReason.java`**

```java
package com.remi.engine.domain;

public enum RejectReason {
  NOT_YOUR_TURN, WRONG_PHASE, GAME_CLOSED,
  STOCK_EMPTY, DISCARD_EMPTY,
  CANNOT_TAKE_OPENING_PIECE, CANNOT_BREAK_LINE, BREAK_REQUIRES_ETALAT,
  PIECE_NOT_IN_HAND, INVALID_MELD,
  FIRST_MELD_TOO_FEW_POINTS, FIRST_MELD_NEEDS_SUITE_OR_1S,
  MUST_USE_TAKEN_PIECE, NOT_ETALAT, INVALID_LAYOFF, HAND_TOO_FULL_TO_DISCARD
}
```

- [ ] **Step 2: Write `RoundResult.java`**

```java
package com.remi.engine.domain;

public record RoundResult(int playerIdx, String name, int base, int melded, int handCount) {}
```

- [ ] **Step 3: Write `DomainEvent.java`**

```java
package com.remi.engine.domain;

import java.util.List;

public sealed interface DomainEvent {
  record TurnStarted(int playerIdx) implements DomainEvent {}
  record CardDrawn(int playerIdx, DrawSource from) implements DomainEvent {}
  record DiscardTaken(int playerIdx, int discardIdx, int taken) implements DomainEvent {}
  record PieceDiscarded(int playerIdx, int pieceId) implements DomainEvent {}
  record PlayerEtalat(int playerIdx, int totalPoints) implements DomainEvent {}
  record LayoffPlayed(int playerIdx, int meldIdx, int pieceId) implements DomainEvent {}
  record RoundClosed(int closerIdx, List<RoundResult> results, boolean withJoker) implements DomainEvent {}
  record StockExhausted() implements DomainEvent {}
}
```

- [ ] **Step 4: Write `ActionResult.java`**

```java
package com.remi.engine.domain;

import java.util.List;

public sealed interface ActionResult {
  record Accepted(GameState newState, List<DomainEvent> events) implements ActionResult {
    public Accepted { events = List.copyOf(events); }
  }
  record Rejected(RejectReason code, String message) implements ActionResult {}
}
```

- [ ] **Step 5: Compile + Commit**

```bash
mvn -q compile
git add src/main/java/com/remi/engine/domain/
git commit -m "feat(engine): add ActionResult, RejectReason, DomainEvent, RoundResult"
```

---

## End of Phase A + B

Continuă cu fișierul `2026-05-16-stage1-game-engine-part2.md` pentru Phase C-J. Acest fișier acoperă scaffolding-ul și domain layer-ul; restul îl scriu separat pentru claritate (oricum execuția merge task-by-task, ordinea fișierelor de plan e accidentală).
