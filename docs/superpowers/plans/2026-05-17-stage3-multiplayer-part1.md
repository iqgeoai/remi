# Stage 3 — Multiplayer + Lobby Implementation Plan (Part 1: Setup → Lobby → Matchmaking)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Remi game playable online with friends — invite-only private games with join codes, public quick-match FIFO matchmaking, on top of Stage 2 auth.

**Architecture:** New packages `com.remi.lobby` (DB linking + lobby + matchmaking + timer services + REST) and `com.remi.ws` (Part 2 — STOMP config + filters + game broadcast). Engine package stays Spring-free. Stage 1 `/api/dev/games/*` stays open. Spec: `docs/superpowers/specs/2026-05-17-stage3-multiplayer-design.md`.

**Tech Stack:** + `spring-boot-starter-websocket` on top of Stage 1+2 stack.

---

## File Structure (Part 1)

```
pom.xml                                                  (modify: add websocket starter)
src/main/resources/
  application.yml                                        (modify: add game-timer key)
  db/migration/V3__multiplayer.sql                       (create)
src/test/resources/
  application-test.yml                                   (modify: add game-timer short ttl)

src/main/java/com/remi/lobby/
  domain/
    GameVisibility.java
    LobbyGame.java
    GamePlayer.java
    MatchConfig.java
  persistence/
    GamePlayerEntity.java
    GamePlayerRepository.java
  service/
    LobbyService.java
    LobbyServiceImpl.java
    MatchmakingService.java
    MatchmakingServiceImpl.java
    JoinCodeGenerator.java
    LobbyNotFoundException.java
    LobbyFullException.java
    AlreadySeatedException.java
    NotSeatedException.java
    NotYourSeatException.java
    GameAlreadyStartedException.java
    JoinCodeNotFoundException.java
    MatchmakingAlreadyQueuedException.java

src/main/java/com/remi/user/persistence/
  GameEntity.java                                        (modify: add owner_id, visibility, join_code columns)

src/main/java/com/remi/service/
  GameService.java                                       (modify: add applyActionAsUser)

src/test/java/com/remi/lobby/
  service/
    JoinCodeGeneratorTest.java
    MatchConfigTest.java
    LobbyServiceIntegrationTest.java
    MatchmakingServiceIntegrationTest.java
src/test/java/com/remi/service/
  GameServiceApplyAsUserIntegrationTest.java
```

---

## Phase A — Setup

### Task A1: Add `spring-boot-starter-websocket` dependency

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add dependency**

Inside the `<dependencies>` block, after `spring-boot-starter-mail`:

```xml
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-websocket</artifactId></dependency>
```

- [ ] **Step 2: Verify**

Run: `mvn -q -DskipTests dependency:resolve`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add spring-boot-starter-websocket for Stage 3 STOMP"
```

---

### Task A2: Flyway V3 migration

**Files:**
- Create: `src/main/resources/db/migration/V3__multiplayer.sql`

- [ ] **Step 1: Write migration**

```sql
ALTER TABLE games
  ADD COLUMN owner_id   UUID REFERENCES users(id),
  ADD COLUMN visibility VARCHAR(10) NOT NULL DEFAULT 'PRIVATE',
  ADD COLUMN join_code  VARCHAR(8);

CREATE UNIQUE INDEX games_join_code_uniq ON games(join_code) WHERE join_code IS NOT NULL;
CREATE INDEX games_visibility_idx ON games(visibility) WHERE visibility = 'PUBLIC';

CREATE TABLE game_players (
  game_id    UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
  player_idx INT  NOT NULL,
  user_id    UUID NOT NULL REFERENCES users(id),
  joined_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (game_id, player_idx)
);
CREATE INDEX game_players_user_idx ON game_players(user_id);
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/db/migration/V3__multiplayer.sql
git commit -m "feat: Flyway V3 — games owner/visibility/join_code + game_players table"
```

---

### Task A3: Config additions for timer

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application-test.yml`

- [ ] **Step 1: Append to `application.yml`** (as a new top-level key, not inside `spring:`)

```yaml
game-timer:
  hard-timeout: PT3M
```

- [ ] **Step 2: Append to `application-test.yml`**

```yaml
game-timer:
  hard-timeout: PT2S
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/application.yml src/test/resources/application-test.yml
git commit -m "feat: game-timer.hard-timeout config (prod 3m, test 2s)"
```

---

## Phase B — Lobby domain + persistence

### Task B1: Domain records (`GameVisibility`, `LobbyGame`, `GamePlayer`, `MatchConfig`)

**Files:**
- Create: `src/main/java/com/remi/lobby/domain/GameVisibility.java`
- Create: `src/main/java/com/remi/lobby/domain/LobbyGame.java`
- Create: `src/main/java/com/remi/lobby/domain/GamePlayer.java`
- Create: `src/main/java/com/remi/lobby/domain/MatchConfig.java`

- [ ] **Step 1: Write all four**

```java
package com.remi.lobby.domain;
public enum GameVisibility { PRIVATE, PUBLIC }
```

```java
package com.remi.lobby.domain;

import com.remi.engine.domain.Difficulty;
import com.remi.engine.domain.Mode;
import java.time.Instant;
import java.util.UUID;

public record LobbyGame(
    UUID id, UUID ownerId, GameVisibility visibility, String joinCode,
    int numPlayers, Mode mode, Difficulty difficulty,
    int seatsTaken, boolean started, Instant createdAt
) {}
```

```java
package com.remi.lobby.domain;

import java.time.Instant;
import java.util.UUID;

public record GamePlayer(UUID gameId, int playerIdx, UUID userId, Instant joinedAt) {}
```

```java
package com.remi.lobby.domain;

import com.remi.engine.domain.Difficulty;
import com.remi.engine.domain.Mode;

public record MatchConfig(int numPlayers, Mode mode, Difficulty difficulty) {}
```

- [ ] **Step 2: Compile + commit**

```bash
mvn -q compile
git add src/main/java/com/remi/lobby/domain/
git commit -m "feat(lobby): add GameVisibility, LobbyGame, GamePlayer, MatchConfig records"
```

---

### Task B2: `MatchConfigTest` (verify equals/hashCode for use as Map key)

**Files:**
- Create: `src/test/java/com/remi/lobby/service/MatchConfigTest.java`

- [ ] **Step 1: Write test**

```java
package com.remi.lobby.service;

import com.remi.engine.domain.Difficulty;
import com.remi.engine.domain.Mode;
import com.remi.lobby.domain.MatchConfig;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class MatchConfigTest {
  @Test void equalsByValue() {
    assertThat(new MatchConfig(2, Mode.ETALAT, Difficulty.MED))
        .isEqualTo(new MatchConfig(2, Mode.ETALAT, Difficulty.MED));
  }
  @Test void differsByNumPlayers() {
    assertThat(new MatchConfig(2, Mode.ETALAT, Difficulty.MED))
        .isNotEqualTo(new MatchConfig(3, Mode.ETALAT, Difficulty.MED));
  }
  @Test void differsByMode() {
    assertThat(new MatchConfig(2, Mode.ETALAT, Difficulty.MED))
        .isNotEqualTo(new MatchConfig(2, Mode.TABLA, Difficulty.MED));
  }
  @Test void usableAsMapKey() {
    Map<MatchConfig, String> map = new HashMap<>();
    MatchConfig key1 = new MatchConfig(2, Mode.ETALAT, Difficulty.MED);
    MatchConfig key2 = new MatchConfig(2, Mode.ETALAT, Difficulty.MED);
    map.put(key1, "value");
    assertThat(map.get(key2)).isEqualTo("value");
  }
}
```

- [ ] **Step 2: Run (PASS — records auto-implement equals/hashCode)** + Commit

```bash
mvn -q test -Dtest=MatchConfigTest
git add src/test/java/com/remi/lobby/service/MatchConfigTest.java
git commit -m "test(lobby): MatchConfig equals/hashCode for Map-key usage"
```

---

### Task B3: Modify `GameEntity` — add `ownerId`, `visibility`, `joinCode` fields

**Files:**
- Modify: `src/main/java/com/remi/persistence/GameEntity.java`

- [ ] **Step 1: Read current `GameEntity.java`**

Note: this file currently has fields `id`, `state`, `version`, `createdAt`, `updatedAt`. We add three more.

- [ ] **Step 2: Add fields + accessors**

Add after the `@Version` field:

```java
  @Column(name = "owner_id")
  private java.util.UUID ownerId;

  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  @Column(name = "visibility", nullable = false, length = 10)
  private com.remi.lobby.domain.GameVisibility visibility = com.remi.lobby.domain.GameVisibility.PRIVATE;

  @Column(name = "join_code", unique = true, length = 8)
  private String joinCode;
```

Add accessors (preserve the existing constructor; add a new convenience constructor for the lobby flow):

```java
  public java.util.UUID getOwnerId() { return ownerId; }
  public void setOwnerId(java.util.UUID ownerId) { this.ownerId = ownerId; }

  public com.remi.lobby.domain.GameVisibility getVisibility() { return visibility; }
  public void setVisibility(com.remi.lobby.domain.GameVisibility visibility) { this.visibility = visibility; }

  public String getJoinCode() { return joinCode; }
  public void setJoinCode(String joinCode) { this.joinCode = joinCode; }
```

- [ ] **Step 3: Compile + commit**

```bash
mvn -q compile
git add src/main/java/com/remi/persistence/GameEntity.java
git commit -m "feat(persistence): GameEntity adds ownerId, visibility, joinCode"
```

---

### Task B4: `GamePlayerEntity` + `GamePlayerRepository`

**Files:**
- Create: `src/main/java/com/remi/lobby/persistence/GamePlayerEntity.java`
- Create: `src/main/java/com/remi/lobby/persistence/GamePlayerRepository.java`

- [ ] **Step 1: Write `GamePlayerEntity.java`** (uses composite primary key)

```java
package com.remi.lobby.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "game_players")
@IdClass(GamePlayerEntity.PK.class)
public class GamePlayerEntity {

  @Id @Column(name = "game_id") private UUID gameId;
  @Id @Column(name = "player_idx") private int playerIdx;

  @Column(name = "user_id", nullable = false) private UUID userId;

  @CreationTimestamp @Column(name = "joined_at", nullable = false, updatable = false)
  private Instant joinedAt;

  protected GamePlayerEntity() {}
  public GamePlayerEntity(UUID gameId, int playerIdx, UUID userId) {
    this.gameId = gameId; this.playerIdx = playerIdx; this.userId = userId;
  }

  public UUID getGameId() { return gameId; }
  public int getPlayerIdx() { return playerIdx; }
  public UUID getUserId() { return userId; }
  public Instant getJoinedAt() { return joinedAt; }

  public static class PK implements Serializable {
    private UUID gameId;
    private int playerIdx;
    public PK() {}
    public PK(UUID gameId, int playerIdx) { this.gameId = gameId; this.playerIdx = playerIdx; }
    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof PK pk)) return false;
      return playerIdx == pk.playerIdx && Objects.equals(gameId, pk.gameId);
    }
    @Override public int hashCode() { return Objects.hash(gameId, playerIdx); }
  }
}
```

- [ ] **Step 2: Write `GamePlayerRepository.java`**

```java
package com.remi.lobby.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GamePlayerRepository extends JpaRepository<GamePlayerEntity, GamePlayerEntity.PK> {
  List<GamePlayerEntity> findByGameIdOrderByPlayerIdxAsc(UUID gameId);
  List<GamePlayerEntity> findByUserId(UUID userId);

  @Query("SELECT COUNT(p) FROM GamePlayerEntity p WHERE p.gameId = :gameId")
  long countByGameId(@Param("gameId") UUID gameId);

  @Query("SELECT p.playerIdx FROM GamePlayerEntity p WHERE p.gameId = :gameId AND p.userId = :userId")
  Optional<Integer> findSeat(@Param("gameId") UUID gameId, @Param("userId") UUID userId);

  boolean existsByGameIdAndUserId(UUID gameId, UUID userId);
}
```

- [ ] **Step 3: Compile + commit**

```bash
mvn -q compile
git add src/main/java/com/remi/lobby/persistence/
git commit -m "feat(lobby): GamePlayerEntity (composite PK) + GamePlayerRepository"
```

---

### Task B5: `JoinCodeGenerator` + TDD

**Files:**
- Create: `src/main/java/com/remi/lobby/service/JoinCodeGenerator.java`
- Create: `src/test/java/com/remi/lobby/service/JoinCodeGeneratorTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.remi.lobby.service;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class JoinCodeGeneratorTest {
  private final JoinCodeGenerator gen = new JoinCodeGenerator();

  @Test void codeIs8CharsLong() {
    assertThat(gen.generate()).hasSize(8);
  }

  @Test void codeOnlyAlphanumericUppercase() {
    String code = gen.generate();
    assertThat(code).matches("^[A-Z0-9]+$");
  }

  @Test void thousandCodesHaveAtLeast999Distinct() {
    Set<String> codes = new HashSet<>();
    for (int i = 0; i < 1000; i++) codes.add(gen.generate());
    assertThat(codes.size()).isGreaterThanOrEqualTo(999);
  }
}
```

- [ ] **Step 2: Run (FAIL — compilation)**

Run: `mvn -q test -Dtest=JoinCodeGeneratorTest`

- [ ] **Step 3: Write `JoinCodeGenerator.java`**

```java
package com.remi.lobby.service;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;

@Component
public class JoinCodeGenerator {
  private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  private static final int CODE_LENGTH = 8;
  private final SecureRandom rng = new SecureRandom();

  public String generate() {
    StringBuilder sb = new StringBuilder(CODE_LENGTH);
    for (int i = 0; i < CODE_LENGTH; i++) {
      sb.append(ALPHABET.charAt(rng.nextInt(ALPHABET.length())));
    }
    return sb.toString();
  }
}
```

- [ ] **Step 4: Run (PASS — 3 tests)** + Commit

```bash
mvn -q test -Dtest=JoinCodeGeneratorTest
git add src/main/java/com/remi/lobby/service/JoinCodeGenerator.java \
        src/test/java/com/remi/lobby/service/JoinCodeGeneratorTest.java
git commit -m "feat(lobby): JoinCodeGenerator (8 chars, alphanumeric uppercase)"
```

---

## Phase C — Lobby exceptions + LobbyService

### Task C1: Lobby exceptions

**Files:**
- Create: `src/main/java/com/remi/lobby/service/LobbyNotFoundException.java`
- Create: `src/main/java/com/remi/lobby/service/LobbyFullException.java`
- Create: `src/main/java/com/remi/lobby/service/AlreadySeatedException.java`
- Create: `src/main/java/com/remi/lobby/service/NotSeatedException.java`
- Create: `src/main/java/com/remi/lobby/service/NotYourSeatException.java`
- Create: `src/main/java/com/remi/lobby/service/GameAlreadyStartedException.java`
- Create: `src/main/java/com/remi/lobby/service/JoinCodeNotFoundException.java`
- Create: `src/main/java/com/remi/lobby/service/MatchmakingAlreadyQueuedException.java`

- [ ] **Step 1: Write all eight**

```java
package com.remi.lobby.service;
import java.util.UUID;
public class LobbyNotFoundException extends RuntimeException {
  public LobbyNotFoundException(UUID id) { super("Lobby not found: " + id); }
}
```
```java
package com.remi.lobby.service;
public class LobbyFullException extends RuntimeException {
  public LobbyFullException() { super("Lobby is full"); }
}
```
```java
package com.remi.lobby.service;
public class AlreadySeatedException extends RuntimeException {
  public AlreadySeatedException() { super("User already seated at this lobby"); }
}
```
```java
package com.remi.lobby.service;
public class NotSeatedException extends RuntimeException {
  public NotSeatedException() { super("User is not seated at this game"); }
}
```
```java
package com.remi.lobby.service;
public class NotYourSeatException extends RuntimeException {
  public NotYourSeatException() { super("Action playerIdx does not match user's seat"); }
}
```
```java
package com.remi.lobby.service;
public class GameAlreadyStartedException extends RuntimeException {
  public GameAlreadyStartedException() { super("Cannot leave: game already started"); }
}
```
```java
package com.remi.lobby.service;
public class JoinCodeNotFoundException extends RuntimeException {
  public JoinCodeNotFoundException(String code) { super("Join code not found: " + code); }
}
```
```java
package com.remi.lobby.service;
public class MatchmakingAlreadyQueuedException extends RuntimeException {
  public MatchmakingAlreadyQueuedException() { super("User is already in matchmaking queue"); }
}
```

- [ ] **Step 2: Compile + commit**

```bash
mvn -q compile
git add src/main/java/com/remi/lobby/service/LobbyNotFoundException.java \
        src/main/java/com/remi/lobby/service/LobbyFullException.java \
        src/main/java/com/remi/lobby/service/AlreadySeatedException.java \
        src/main/java/com/remi/lobby/service/NotSeatedException.java \
        src/main/java/com/remi/lobby/service/NotYourSeatException.java \
        src/main/java/com/remi/lobby/service/GameAlreadyStartedException.java \
        src/main/java/com/remi/lobby/service/JoinCodeNotFoundException.java \
        src/main/java/com/remi/lobby/service/MatchmakingAlreadyQueuedException.java
git commit -m "feat(lobby): add 8 service exceptions (LobbyFull, NotSeated, NotYourSeat, etc.)"
```

---

### Task C2: `LobbyService` interface + `LobbyServiceImpl` + IT

**Files:**
- Create: `src/main/java/com/remi/lobby/service/LobbyService.java`
- Create: `src/main/java/com/remi/lobby/service/LobbyServiceImpl.java`
- Create: `src/test/java/com/remi/lobby/service/LobbyServiceIntegrationTest.java`

- [ ] **Step 1: Write interface**

```java
package com.remi.lobby.service;

import com.remi.engine.domain.Difficulty;
import com.remi.engine.domain.Mode;
import com.remi.lobby.domain.LobbyGame;
import java.util.List;
import java.util.UUID;

public interface LobbyService {
  LobbyGame createPrivate(UUID ownerId, int numPlayers, Mode mode, Difficulty diff);
  LobbyGame createPublic(UUID ownerId, int numPlayers, Mode mode, Difficulty diff);
  LobbyGame createPublicForUsers(List<UUID> userIds, int numPlayers, Mode mode, Difficulty diff);
  LobbyGame joinByCode(UUID userId, String joinCode);
  LobbyGame joinPublic(UUID userId, UUID gameId);
  List<LobbyGame> listPublicWaiting();
  List<LobbyGame> myGames(UUID userId);
  void leave(UUID userId, UUID gameId);
  LobbyGame get(UUID gameId);
}
```

- [ ] **Step 2: Write failing IT**

```java
package com.remi.lobby.service;

import com.remi.engine.domain.Difficulty;
import com.remi.engine.domain.Mode;
import com.remi.lobby.domain.GameVisibility;
import com.remi.lobby.domain.LobbyGame;
import com.remi.lobby.persistence.GamePlayerRepository;
import com.remi.user.api.MockMailServiceTestConfig;
import com.remi.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(MockMailServiceTestConfig.class)
class LobbyServiceIntegrationTest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired LobbyService lobbyService;
  @Autowired UserService userService;
  @Autowired GamePlayerRepository playersRepo;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void reset() {
    MockMailServiceTestConfig.SENT.clear();
    jdbc.execute("TRUNCATE game_players, games, refresh_tokens, verification_tokens, password_reset_tokens, users CASCADE");
  }

  private UUID registerVerified(String email, String username) {
    var u = userService.register(email, username, "passwordxx");
    userService.verifyEmail(MockMailServiceTestConfig.SENT.get(0).token());
    return u.id();
  }

  @Test
  void createPrivateInsertsGameWithCodeAndSeatsOwner() {
    UUID owner = registerVerified("a@b.com", "alice");
    LobbyGame g = lobbyService.createPrivate(owner, 3, Mode.ETALAT, Difficulty.MED);
    assertThat(g.ownerId()).isEqualTo(owner);
    assertThat(g.visibility()).isEqualTo(GameVisibility.PRIVATE);
    assertThat(g.joinCode()).isNotNull().hasSize(8);
    assertThat(g.seatsTaken()).isEqualTo(1);
    assertThat(g.started()).isFalse();
    assertThat(playersRepo.findSeat(g.id(), owner)).hasValue(0);
  }

  @Test
  void createPublicHasNoJoinCode() {
    UUID owner = registerVerified("a@b.com", "alice");
    LobbyGame g = lobbyService.createPublic(owner, 2, Mode.ETALAT, Difficulty.MED);
    assertThat(g.visibility()).isEqualTo(GameVisibility.PUBLIC);
    assertThat(g.joinCode()).isNull();
  }

  @Test
  void joinByCodeAddsPlayer() {
    UUID owner = registerVerified("a@b.com", "alice");
    UUID guest = registerVerified("b@c.com", "bob");
    LobbyGame g = lobbyService.createPrivate(owner, 3, Mode.ETALAT, Difficulty.MED);
    LobbyGame after = lobbyService.joinByCode(guest, g.joinCode());
    assertThat(after.seatsTaken()).isEqualTo(2);
    assertThat(after.started()).isFalse();
    assertThat(playersRepo.findSeat(g.id(), guest)).hasValue(1);
  }

  @Test
  void joinByCodeRejectsUnknownCode() {
    UUID guest = registerVerified("b@c.com", "bob");
    assertThatThrownBy(() -> lobbyService.joinByCode(guest, "BAD12345"))
        .isInstanceOf(JoinCodeNotFoundException.class);
  }

  @Test
  void joinByCodeRejectsAlreadySeated() {
    UUID owner = registerVerified("a@b.com", "alice");
    LobbyGame g = lobbyService.createPrivate(owner, 3, Mode.ETALAT, Difficulty.MED);
    assertThatThrownBy(() -> lobbyService.joinByCode(owner, g.joinCode()))
        .isInstanceOf(AlreadySeatedException.class);
  }

  @Test
  void joinByCodeRejectsFullLobby() {
    UUID a = registerVerified("a@b.com", "alice");
    UUID b = registerVerified("b@c.com", "bob");
    UUID c = registerVerified("c@d.com", "carol");
    LobbyGame g = lobbyService.createPrivate(a, 2, Mode.ETALAT, Difficulty.MED);
    lobbyService.joinByCode(b, g.joinCode());  // fills lobby; should auto-start
    assertThatThrownBy(() -> lobbyService.joinByCode(c, g.joinCode()))
        .isInstanceOf(LobbyFullException.class);
  }

  @Test
  void lastJoinerStartsGame() {
    UUID a = registerVerified("a@b.com", "alice");
    UUID b = registerVerified("b@c.com", "bob");
    LobbyGame g = lobbyService.createPrivate(a, 2, Mode.ETALAT, Difficulty.MED);
    LobbyGame after = lobbyService.joinByCode(b, g.joinCode());
    assertThat(after.started()).isTrue();
  }

  @Test
  void listPublicWaitingShowsOnlyPublicNotFull() {
    UUID a = registerVerified("a@b.com", "alice");
    UUID b = registerVerified("b@c.com", "bob");
    lobbyService.createPrivate(a, 3, Mode.ETALAT, Difficulty.MED);   // private excluded
    LobbyGame pub = lobbyService.createPublic(b, 3, Mode.ETALAT, Difficulty.MED);
    var list = lobbyService.listPublicWaiting();
    assertThat(list).extracting(LobbyGame::id).containsExactly(pub.id());
  }

  @Test
  void myGamesListsAllSeatedGames() {
    UUID alice = registerVerified("a@b.com", "alice");
    UUID bob = registerVerified("b@c.com", "bob");
    LobbyGame g1 = lobbyService.createPrivate(alice, 3, Mode.ETALAT, Difficulty.MED);
    LobbyGame g2 = lobbyService.createPrivate(bob, 3, Mode.ETALAT, Difficulty.MED);
    lobbyService.joinByCode(alice, g2.joinCode());
    var mine = lobbyService.myGames(alice);
    assertThat(mine).extracting(LobbyGame::id).containsExactlyInAnyOrder(g1.id(), g2.id());
  }

  @Test
  void leaveBeforeStartedSucceeds() {
    UUID alice = registerVerified("a@b.com", "alice");
    UUID bob = registerVerified("b@c.com", "bob");
    LobbyGame g = lobbyService.createPrivate(alice, 3, Mode.ETALAT, Difficulty.MED);
    lobbyService.joinByCode(bob, g.joinCode());
    lobbyService.leave(bob, g.id());
    assertThat(lobbyService.get(g.id()).seatsTaken()).isEqualTo(1);
  }

  @Test
  void leaveAfterStartedRejected() {
    UUID alice = registerVerified("a@b.com", "alice");
    UUID bob = registerVerified("b@c.com", "bob");
    LobbyGame g = lobbyService.createPrivate(alice, 2, Mode.ETALAT, Difficulty.MED);
    lobbyService.joinByCode(bob, g.joinCode());  // started
    assertThatThrownBy(() -> lobbyService.leave(alice, g.id()))
        .isInstanceOf(GameAlreadyStartedException.class);
  }
}
```

- [ ] **Step 3: Run (FAIL — implementation missing)**

- [ ] **Step 4: Write `LobbyServiceImpl.java`**

```java
package com.remi.lobby.service;

import com.remi.engine.domain.Difficulty;
import com.remi.engine.domain.GameState;
import com.remi.engine.domain.Mode;
import com.remi.engine.rules.Dealer;
import com.remi.lobby.domain.GameVisibility;
import com.remi.lobby.domain.LobbyGame;
import com.remi.lobby.persistence.GamePlayerEntity;
import com.remi.lobby.persistence.GamePlayerRepository;
import com.remi.persistence.GameEntity;
import com.remi.persistence.GameRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class LobbyServiceImpl implements LobbyService {
  private static final int JOIN_CODE_RETRIES = 5;

  private final GameRepository games;
  private final GamePlayerRepository players;
  private final JoinCodeGenerator codeGen;
  private final Clock clock;

  public LobbyServiceImpl(GameRepository games, GamePlayerRepository players,
                          JoinCodeGenerator codeGen, Clock clock) {
    this.games = games;
    this.players = players;
    this.codeGen = codeGen;
    this.clock = clock;
  }

  @Override
  @Transactional
  public LobbyGame createPrivate(UUID ownerId, int numPlayers, Mode mode, Difficulty diff) {
    GameState s = Dealer.deal(numPlayers, mode, diff, System.nanoTime());
    GameEntity e = new GameEntity(s.id(), s);
    e.setOwnerId(ownerId);
    e.setVisibility(GameVisibility.PRIVATE);
    e.setJoinCode(generateUniqueCode());
    games.save(e);
    players.save(new GamePlayerEntity(s.id(), 0, ownerId));
    return toLobbyGame(e, 1);
  }

  @Override
  @Transactional
  public LobbyGame createPublic(UUID ownerId, int numPlayers, Mode mode, Difficulty diff) {
    GameState s = Dealer.deal(numPlayers, mode, diff, System.nanoTime());
    GameEntity e = new GameEntity(s.id(), s);
    e.setOwnerId(ownerId);
    e.setVisibility(GameVisibility.PUBLIC);
    e.setJoinCode(null);
    games.save(e);
    players.save(new GamePlayerEntity(s.id(), 0, ownerId));
    return toLobbyGame(e, 1);
  }

  @Override
  @Transactional
  public LobbyGame createPublicForUsers(List<UUID> userIds, int numPlayers, Mode mode, Difficulty diff) {
    if (userIds.size() != numPlayers) throw new IllegalArgumentException("userIds.size must equal numPlayers");
    GameState s = Dealer.deal(numPlayers, mode, diff, System.nanoTime());
    GameEntity e = new GameEntity(s.id(), s);
    e.setOwnerId(userIds.get(0));
    e.setVisibility(GameVisibility.PUBLIC);
    e.setJoinCode(null);
    games.save(e);
    for (int i = 0; i < userIds.size(); i++) {
      players.save(new GamePlayerEntity(s.id(), i, userIds.get(i)));
    }
    return toLobbyGame(e, numPlayers);
  }

  @Override
  @Transactional
  public LobbyGame joinByCode(UUID userId, String joinCode) {
    GameEntity e = games.findByJoinCode(joinCode).orElseThrow(() -> new JoinCodeNotFoundException(joinCode));
    return joinInternal(userId, e);
  }

  @Override
  @Transactional
  public LobbyGame joinPublic(UUID userId, UUID gameId) {
    GameEntity e = games.findById(gameId).orElseThrow(() -> new LobbyNotFoundException(gameId));
    if (e.getVisibility() != GameVisibility.PUBLIC) throw new LobbyNotFoundException(gameId);
    return joinInternal(userId, e);
  }

  private LobbyGame joinInternal(UUID userId, GameEntity e) {
    int numPlayers = e.getState().players().size();
    long seats = players.countByGameId(e.getId());
    if (players.existsByGameIdAndUserId(e.getId(), userId)) throw new AlreadySeatedException();
    if (seats >= numPlayers) throw new LobbyFullException();
    try {
      players.save(new GamePlayerEntity(e.getId(), (int) seats, userId));
    } catch (DataIntegrityViolationException dup) {
      throw new LobbyFullException();   // race: someone took the seat
    }
    return toLobbyGame(e, (int) seats + 1);
  }

  @Override
  @Transactional(readOnly = true)
  public List<LobbyGame> listPublicWaiting() {
    return games.findByVisibility(GameVisibility.PUBLIC).stream()
        .map(e -> toLobbyGame(e, (int) players.countByGameId(e.getId())))
        .filter(g -> g.seatsTaken() < g.numPlayers())
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<LobbyGame> myGames(UUID userId) {
    return players.findByUserId(userId).stream()
        .map(p -> games.findById(p.getGameId()).orElseThrow())
        .distinct()
        .map(e -> toLobbyGame(e, (int) players.countByGameId(e.getId())))
        .toList();
  }

  @Override
  @Transactional
  public void leave(UUID userId, UUID gameId) {
    GameEntity e = games.findById(gameId).orElseThrow(() -> new LobbyNotFoundException(gameId));
    long seats = players.countByGameId(gameId);
    if (seats >= e.getState().players().size()) throw new GameAlreadyStartedException();
    int seat = players.findSeat(gameId, userId).orElseThrow(NotSeatedException::new);
    players.deleteById(new GamePlayerEntity.PK(gameId, seat));
    if (userId.equals(e.getOwnerId())) {
      games.deleteById(gameId);     // cascade deletes remaining game_players
    }
  }

  @Override
  @Transactional(readOnly = true)
  public LobbyGame get(UUID gameId) {
    GameEntity e = games.findById(gameId).orElseThrow(() -> new LobbyNotFoundException(gameId));
    return toLobbyGame(e, (int) players.countByGameId(gameId));
  }

  private String generateUniqueCode() {
    for (int i = 0; i < JOIN_CODE_RETRIES; i++) {
      String code = codeGen.generate();
      if (games.findByJoinCode(code).isEmpty()) return code;
    }
    throw new IllegalStateException("Could not generate unique join code after " + JOIN_CODE_RETRIES + " tries");
  }

  private LobbyGame toLobbyGame(GameEntity e, int seatsTaken) {
    GameState s = e.getState();
    boolean started = seatsTaken == s.players().size();
    return new LobbyGame(
        e.getId(), e.getOwnerId(), e.getVisibility(), e.getJoinCode(),
        s.players().size(), s.mode(), s.difficulty(),
        seatsTaken, started, e.getCreatedAt()
    );
  }
}
```

- [ ] **Step 5: Update `GameRepository.java` with new lookup methods**

Modify `src/main/java/com/remi/persistence/GameRepository.java` to add:

```java
  java.util.Optional<GameEntity> findByJoinCode(String joinCode);
  java.util.List<GameEntity> findByVisibility(com.remi.lobby.domain.GameVisibility visibility);
```

- [ ] **Step 6: Run (PASS — 11 tests, Docker required)** + Commit

```bash
mvn test -Dtest=LobbyServiceIntegrationTest
git add src/main/java/com/remi/lobby/service/LobbyService.java \
        src/main/java/com/remi/lobby/service/LobbyServiceImpl.java \
        src/main/java/com/remi/persistence/GameRepository.java \
        src/test/java/com/remi/lobby/service/LobbyServiceIntegrationTest.java
git commit -m "feat(lobby): LobbyService — create, joinByCode, list, myGames, leave + IT"
```

---

## Phase D — MatchmakingService

### Task D1: `MatchmakingService` + IT

**Files:**
- Create: `src/main/java/com/remi/lobby/service/MatchmakingService.java`
- Create: `src/main/java/com/remi/lobby/service/MatchmakingServiceImpl.java`
- Create: `src/test/java/com/remi/lobby/service/MatchmakingServiceIntegrationTest.java`

- [ ] **Step 1: Write interface**

```java
package com.remi.lobby.service;

import com.remi.lobby.domain.LobbyGame;
import com.remi.lobby.domain.MatchConfig;
import java.util.Optional;
import java.util.UUID;

public interface MatchmakingService {
  Optional<LobbyGame> enqueue(UUID userId, MatchConfig config);
  void cancel(UUID userId);
  int queueDepth(MatchConfig config);
}
```

- [ ] **Step 2: Write failing IT**

```java
package com.remi.lobby.service;

import com.remi.engine.domain.Difficulty;
import com.remi.engine.domain.Mode;
import com.remi.lobby.domain.LobbyGame;
import com.remi.lobby.domain.MatchConfig;
import com.remi.user.api.MockMailServiceTestConfig;
import com.remi.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(MockMailServiceTestConfig.class)
class MatchmakingServiceIntegrationTest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired MatchmakingService matchService;
  @Autowired UserService userService;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach void reset() {
    MockMailServiceTestConfig.SENT.clear();
    jdbc.execute("TRUNCATE game_players, games, refresh_tokens, verification_tokens, password_reset_tokens, users CASCADE");
  }

  private UUID registerVerified(String email, String username) {
    var u = userService.register(email, username, "passwordxx");
    userService.verifyEmail(MockMailServiceTestConfig.SENT.get(0).token());
    return u.id();
  }

  private MatchConfig cfg(int n) { return new MatchConfig(n, Mode.ETALAT, Difficulty.MED); }

  @Test
  void singleEnqueueQueues() {
    UUID a = registerVerified("a@b.com", "alice");
    Optional<LobbyGame> result = matchService.enqueue(a, cfg(2));
    assertThat(result).isEmpty();
    assertThat(matchService.queueDepth(cfg(2))).isEqualTo(1);
  }

  @Test
  void twoEnqueueMatches() {
    UUID a = registerVerified("a@b.com", "alice");
    UUID b = registerVerified("b@c.com", "bob");
    matchService.enqueue(a, cfg(2));
    Optional<LobbyGame> result = matchService.enqueue(b, cfg(2));
    assertThat(result).isPresent();
    assertThat(result.get().seatsTaken()).isEqualTo(2);
    assertThat(result.get().started()).isTrue();
    assertThat(matchService.queueDepth(cfg(2))).isZero();
  }

  @Test
  void differentConfigsKeepSeparateQueues() {
    UUID a = registerVerified("a@b.com", "alice");
    UUID b = registerVerified("b@c.com", "bob");
    matchService.enqueue(a, cfg(2));
    matchService.enqueue(b, cfg(3));
    assertThat(matchService.queueDepth(cfg(2))).isEqualTo(1);
    assertThat(matchService.queueDepth(cfg(3))).isEqualTo(1);
  }

  @Test
  void cancelRemovesFromQueue() {
    UUID a = registerVerified("a@b.com", "alice");
    matchService.enqueue(a, cfg(2));
    matchService.cancel(a);
    assertThat(matchService.queueDepth(cfg(2))).isZero();
  }

  @Test
  void doubleEnqueueRejected() {
    UUID a = registerVerified("a@b.com", "alice");
    matchService.enqueue(a, cfg(2));
    assertThatThrownBy(() -> matchService.enqueue(a, cfg(2)))
        .isInstanceOf(MatchmakingAlreadyQueuedException.class);
  }

  @Test
  void concurrentEnqueueMakesCleanMatches() throws Exception {
    int total = 4;
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < total; i++) ids.add(registerVerified("u" + i + "@e.com", "u" + i));

    ExecutorService ex = Executors.newFixedThreadPool(total);
    List<Future<Optional<LobbyGame>>> futures = new ArrayList<>();
    CountDownLatch start = new CountDownLatch(1);
    for (UUID id : ids) {
      futures.add(ex.submit(() -> {
        start.await();
        return matchService.enqueue(id, cfg(2));
      }));
    }
    start.countDown();
    int matches = 0;
    for (Future<Optional<LobbyGame>> f : futures) {
      if (f.get(5, TimeUnit.SECONDS).isPresent()) matches++;
    }
    ex.shutdown();
    // Exactly 2 calls (the second of each pair) should return a match.
    assertThat(matches).isEqualTo(2);
    assertThat(matchService.queueDepth(cfg(2))).isZero();
  }
}
```

- [ ] **Step 3: Run (FAIL)**

- [ ] **Step 4: Write `MatchmakingServiceImpl.java`**

```java
package com.remi.lobby.service;

import com.remi.lobby.domain.LobbyGame;
import com.remi.lobby.domain.MatchConfig;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MatchmakingServiceImpl implements MatchmakingService {
  private final LobbyService lobby;
  private final Map<MatchConfig, Deque<UUID>> queues = new ConcurrentHashMap<>();
  private final Set<UUID> queuedUsers = ConcurrentHashMap.newKeySet();

  public MatchmakingServiceImpl(LobbyService lobby) { this.lobby = lobby; }

  @Override
  public Optional<LobbyGame> enqueue(UUID userId, MatchConfig config) {
    Deque<UUID> q = queues.computeIfAbsent(config, k -> new ArrayDeque<>());
    synchronized (q) {
      if (!queuedUsers.add(userId)) throw new MatchmakingAlreadyQueuedException();
      q.add(userId);
      if (q.size() >= config.numPlayers()) {
        List<UUID> picked = new ArrayList<>(config.numPlayers());
        for (int i = 0; i < config.numPlayers(); i++) picked.add(q.poll());
        picked.forEach(queuedUsers::remove);
        LobbyGame game = lobby.createPublicForUsers(picked, config.numPlayers(), config.mode(), config.difficulty());
        return Optional.of(game);
      }
      return Optional.empty();
    }
  }

  @Override
  public void cancel(UUID userId) {
    queuedUsers.remove(userId);
    for (Deque<UUID> q : queues.values()) {
      synchronized (q) { q.remove(userId); }
    }
  }

  @Override
  public int queueDepth(MatchConfig config) {
    Deque<UUID> q = queues.get(config);
    if (q == null) return 0;
    synchronized (q) { return q.size(); }
  }
}
```

- [ ] **Step 5: Run (PASS — 6 tests, Docker required)** + Commit

```bash
mvn test -Dtest=MatchmakingServiceIntegrationTest
git add src/main/java/com/remi/lobby/service/MatchmakingService.java \
        src/main/java/com/remi/lobby/service/MatchmakingServiceImpl.java \
        src/test/java/com/remi/lobby/service/MatchmakingServiceIntegrationTest.java
git commit -m "feat(lobby): MatchmakingService — FIFO queue per (n,mode,diff), concurrent-safe"
```

---

## Phase E — Modify `GameService`

### Task E1: Add `applyActionAsUser` to `GameService` + IT

**Files:**
- Modify: `src/main/java/com/remi/service/GameService.java`
- Create: `src/test/java/com/remi/service/GameServiceApplyAsUserIntegrationTest.java`

- [ ] **Step 1: Read `GameService.java`** — current methods: `create`, `applyAction`, `runBotsUntilHuman`, `get`.

- [ ] **Step 2: Add `applyActionAsUser` method**

Add at the end of the class (before closing `}`):

```java
  @Transactional
  public com.remi.engine.domain.GameState applyActionAsUser(
      java.util.UUID gameId, java.util.UUID userId, com.remi.engine.domain.Action action) {
    int seat = playerSeats.findSeat(gameId, userId)
        .orElseThrow(com.remi.lobby.service.NotSeatedException::new);
    if (action.playerIdx() != seat) throw new com.remi.lobby.service.NotYourSeatException();
    return applyAction(gameId, action);
  }
```

Add a new field + constructor parameter for `GamePlayerRepository playerSeats`. Modify the constructor signature and field declaration accordingly. Original constructor takes `(GameRepository repo)`; new signature: `(GameRepository repo, GamePlayerRepository playerSeats)`.

The full updated constructor:

```java
  private final GameRepository repo;
  private final com.remi.lobby.persistence.GamePlayerRepository playerSeats;

  public GameService(GameRepository repo, com.remi.lobby.persistence.GamePlayerRepository playerSeats) {
    this.repo = repo;
    this.playerSeats = playerSeats;
  }
```

- [ ] **Step 3: Write failing IT**

```java
package com.remi.service;

import com.remi.engine.domain.*;
import com.remi.lobby.service.LobbyService;
import com.remi.lobby.service.NotSeatedException;
import com.remi.lobby.service.NotYourSeatException;
import com.remi.user.api.MockMailServiceTestConfig;
import com.remi.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(MockMailServiceTestConfig.class)
class GameServiceApplyAsUserIntegrationTest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired GameService gameService;
  @Autowired LobbyService lobby;
  @Autowired UserService userService;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach void reset() {
    MockMailServiceTestConfig.SENT.clear();
    jdbc.execute("TRUNCATE game_players, games, refresh_tokens, verification_tokens, password_reset_tokens, users CASCADE");
  }

  private UUID registerVerified(String email, String username) {
    var u = userService.register(email, username, "passwordxx");
    userService.verifyEmail(MockMailServiceTestConfig.SENT.get(0).token());
    return u.id();
  }

  @Test
  void seatedUserWithCorrectPlayerIdxAccepted() {
    UUID a = registerVerified("a@b.com", "alice");
    UUID b = registerVerified("b@c.com", "bob");
    var g = lobby.createPrivate(a, 2, Mode.ETALAT, Difficulty.MED);
    lobby.joinByCode(b, g.joinCode());
    GameState state = gameService.get(g.id());
    int firstPiece = state.players().get(0).hand().get(0).id();
    GameState after = gameService.applyActionAsUser(g.id(), a, new Action.Discard(0, firstPiece));
    assertThat(after.players().get(0).hand()).hasSize(14);
  }

  @Test
  void seatedUserWrongPlayerIdxRejected() {
    UUID a = registerVerified("a@b.com", "alice");
    UUID b = registerVerified("b@c.com", "bob");
    var g = lobby.createPrivate(a, 2, Mode.ETALAT, Difficulty.MED);
    lobby.joinByCode(b, g.joinCode());
    assertThatThrownBy(() -> gameService.applyActionAsUser(g.id(), a, new Action.Discard(1, 0)))
        .isInstanceOf(NotYourSeatException.class);
  }

  @Test
  void nonSeatedUserRejected() {
    UUID a = registerVerified("a@b.com", "alice");
    UUID b = registerVerified("b@c.com", "bob");
    UUID c = registerVerified("c@d.com", "carol");
    var g = lobby.createPrivate(a, 2, Mode.ETALAT, Difficulty.MED);
    lobby.joinByCode(b, g.joinCode());
    assertThatThrownBy(() -> gameService.applyActionAsUser(g.id(), c, new Action.Discard(0, 0)))
        .isInstanceOf(NotSeatedException.class);
  }
}
```

- [ ] **Step 4: Run (PASS — 3 tests)** + Commit

```bash
mvn test -Dtest=GameServiceApplyAsUserIntegrationTest
git add src/main/java/com/remi/service/GameService.java \
        src/test/java/com/remi/service/GameServiceApplyAsUserIntegrationTest.java
git commit -m "feat(service): GameService.applyActionAsUser (seat validation) + IT"
```

---

## End of Part 1

Continuă în Part 2 (`2026-05-17-stage3-multiplayer-part2.md`) — Phases F (WebSocket infrastructure), G (GameTimerService), H (broadcast + WS controller), I (REST controllers + exception handler), J (E2E + coverage + README).
