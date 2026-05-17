# Stage 3 — Multiplayer + Lobby (Design)

**Data:** 2026-05-17
**Status:** Approved
**Scope:** Stage 3 din planul multi-etape Remi. Vezi `2026-05-16-stage1-game-engine-design.md` și `2026-05-16-stage2-auth-design.md` pentru context.

## Context

Stage 1 a livrat engine + persistență + REST dev. Stage 2 a adăugat auth + users (JWT, register, verify, login, refresh, password reset). Stage 3 face jocul **jucabil cu prieteni online**: doi sau mai mulți useri autentificați pot să se așeze la aceeași masă și să joace în timp real cu broadcast pe WebSocket.

Endpoint-urile `/api/dev/games/*` rămân deschise pentru dev/debugging.

## Cerințe (confirmate cu user)

- **Scope**: minim viabil multiplayer **+ public matchmaking**
  - DB linking user↔game, lobby invite-only cu cod, WebSocket auth, real-time broadcast, hybrid turn timer, `/api/games/*` autenticat, plus matchmaking public FIFO
- **Matchmaking**: quick-match simplu, queue FIFO per `(numPlayers, mode, difficulty)`, fără rating
- **WebSocket**: **STOMP peste WebSocket cu SockJS fallback**
- **Reconnect**: simplu — client re-CONNECT cu același JWT și re-SUBSCRIBE; server nu păstrează stare per-sesiune; GET REST întoarce state curent
- **Turn timer**: **hybrid** — server fallback la 180s, client tries `ForceAuto` la 120s
- **`/api/dev/games/*`**: rămân deschise neatinse (Stage 1 backdoor)

Deferate (afișate aici pentru limpezime, NU implementate în Stage 3):
- Skill-based matchmaking / ELO (Stage 8)
- Spectator mode
- Game-level chat (Stage 7)
- Rate limiting pe matchmaking (Stage 8)
- Multi-instance scaling cu Redis pub/sub
- Reconnect cu state diff (Stage 3 face full GET)
- Tournament

## Stack tehnic adăugat

- `spring-boot-starter-websocket` (STOMP server + SockJS)

Restul stack-ului rămâne identic.

## 1. Arhitectură

Pachete noi:

```
com.remi.lobby
  .domain          ← LobbyGame, GamePlayer, MatchmakingRequest, MatchConfig, GameVisibility
  .persistence     ← GamePlayerEntity, GamePlayerRepository
                   ← (Game entity din Stage 1 primește owner_id + visibility + join_code coloane)
  .service         ← LobbyService, MatchmakingService, GameTimerService
  .api             ← LobbyController (/api/games/*), MatchmakingController (/api/matchmaking/*), DTOs
com.remi.ws
  .config          ← WebSocketConfig (STOMP + SockJS endpoint /ws)
                   ← StompAuthChannelInterceptor (JWT pe CONNECT)
                   ← StompSubscriptionInterceptor (autorizare pe SUBSCRIBE)
  .controller      ← GameWsController (@MessageMapping pentru actions inbound)
                   ← WsExceptionHandler (@ControllerAdvice pentru erori)
  .broadcast       ← GameBroadcaster (trimite per-user GameView)
```

**Modificări la `com.remi.service.GameService` (Stage 1):**
- Hook post-apply care invocă `GameBroadcaster.broadcastState`
- Metodă nouă `applyActionAsUser(gameId, userId, action)` care verifică user-ul e seated la indexul corect (delegă la `applyAction` după validare)

**Regula păstrată:** `com.remi.engine.*` rămâne fără Spring/JPA. Toate noutățile sunt în `lobby` și `ws`.

**State păstrat in-memory (single-instance):**
- `MatchmakingService`: `Map<MatchConfig, Queue<UUID>>`
- `GameTimerService`: `Map<UUID, ScheduledFuture>`
- WS sessions: Spring `SimpUserRegistry`

Acceptabil la restart: queue matchmaking pierdut (user re-enqueue), timer-ele pierdute (la următoarea acțiune se programează nou).

## 2. Componente

### 2.1 Schema DB (Flyway V3)

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

`visibility` = `PRIVATE` (invite-only, are `join_code`) sau `PUBLIC` (vizibil în quick-match). Default `PRIVATE` pentru games existente.

### 2.2 Domain records

```java
public enum GameVisibility { PRIVATE, PUBLIC }

public record LobbyGame(
    UUID id, UUID ownerId, GameVisibility visibility, String joinCode,
    int numPlayers, Mode mode, Difficulty difficulty,
    int seatsTaken, boolean started, Instant createdAt
) {}

public record GamePlayer(UUID gameId, int playerIdx, UUID userId, Instant joinedAt) {}

public record MatchConfig(int numPlayers, Mode mode, Difficulty difficulty) {}
```

### 2.3 Service interfaces

```java
public interface LobbyService {
  LobbyGame createPrivate(UUID ownerId, int numPlayers, Mode mode, Difficulty diff);
  LobbyGame createPublic(UUID ownerId, int numPlayers, Mode mode, Difficulty diff);
  LobbyGame joinByCode(UUID userId, String joinCode);
  List<LobbyGame> listPublicWaiting();
  List<LobbyGame> myGames(UUID userId);
  void leave(UUID userId, UUID gameId);
}

public interface MatchmakingService {
  Optional<LobbyGame> enqueue(UUID userId, MatchConfig config);   // empty = still queued
  void cancel(UUID userId);
  int queueDepth(MatchConfig config);
}

public interface GameTimerService {
  void scheduleHardTimeout(UUID gameId, int playerIdx, Runnable onTimeout);
  void cancel(UUID gameId);
}

public interface GameBroadcaster {
  void broadcastState(UUID gameId, GameState newState, List<DomainEvent> events);
}
```

**Decisión:** jocul pornește automat când `seatsTaken == numPlayers` (fără buton "start" explicit).

### 2.4 REST API

```
POST /api/games                         (auth) → 201 + LobbyGame
     {visibility, numPlayers:2-6, mode, difficulty}

POST /api/games/{id}/join               (auth) → 200 + LobbyGame  (public games only)
POST /api/games/join-by-code            (auth) → 200 + LobbyGame  {joinCode}
GET  /api/games/public                  (auth) → 200 + List<LobbyGame>
GET  /api/games/mine                    (auth) → 200 + List<LobbyGame>
GET  /api/games/{id}                    (auth) → 200 + GameView    (must be seated)
POST /api/games/{id}/actions            (auth) → 200 + GameView    (must be seated; playerIdx must match seat)
POST /api/games/{id}/leave              (auth) → 204               (before started)

POST /api/matchmaking/quick             (auth) → 200 + {matched:true, game:LobbyGame} | {matched:false}
     {numPlayers, mode, difficulty}
POST /api/matchmaking/cancel            (auth) → 204
```

`/api/dev/games/*` rămân neatinse.

### 2.5 WebSocket

**Endpoint:** `ws://host/ws` cu SockJS fallback.
**Broker:** in-memory simple broker, prefixes:
- `/topic/...` — broadcast pe topic
- `/user/queue/...` — per-user direct
- `/app/...` — inbound de la client (mapped la `@MessageMapping`)

**Destinations:**
- `/app/games/{gameId}/actions` — inbound (client trimite Action)
- `/user/queue/games/{gameId}` — per-user state broadcast (cu hand-ul propriu vizibil, hand-urile celorlalți ascunse)
- `/user/queue/match` — matchmaking notification
- `/user/queue/errors` — feedback la propriile mutări invalide

**Auth handshake (CONNECT):**

```java
class StompAuthChannelInterceptor implements ChannelInterceptor {
  Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
      String authHeader = accessor.getFirstNativeHeader("Authorization");
      if (authHeader == null || !authHeader.startsWith("Bearer ")) throw new MessagingException("missing auth");
      JwtClaims claims = jwt.parseAccessToken(authHeader.substring(7));
      accessor.setUser(new StompPrincipal(claims.userId()));
    }
    return message;
  }
}
```

**Subscription authorization:** la `SUBSCRIBE` pe `/user/queue/games/{id}`, interceptor verifică user e seated la `gameId`.

### 2.6 Modificări la `GameService`

```java
public GameState applyActionAsUser(UUID gameId, UUID userId, Action action) {
  int seatIdx = gamePlayerRepo.findSeat(gameId, userId).orElseThrow(NotSeatedException::new);
  if (action.playerIdx() != seatIdx) throw new NotYourSeatException();
  GameState newState = applyAction(gameId, action);  // existing method
  return newState;
}
```

`GameWsController` și `LobbyController.actions` apelează `applyActionAsUser`. Endpoint-ul Stage 1 `/api/dev/games/{id}/actions` continuă să folosească `applyAction` direct (no seat check).

## 3. Data flow

### 3.1 Create private game

```
POST /api/games {visibility:PRIVATE, numPlayers:3, mode:ETALAT, difficulty:MED}
→ LobbyService.createPrivate(userId, 3, ETALAT, MED):
  1. GameState s = Dealer.deal(3, ETALAT, MED, randomSeed)
  2. joinCode = randomCode(8 chars), retry on conflict
  3. INSERT games(id=s.id, state=s, owner_id=userId, visibility=PRIVATE, join_code=joinCode)
  4. INSERT game_players(game_id=s.id, player_idx=0, user_id=userId)
  5. return LobbyGame
→ HTTP 201
```

### 3.2 Join by code

```
POST /api/games/join-by-code {joinCode}
→ LobbyService.joinByCode(userId, code):
  1. SELECT games WHERE join_code=:code (404 if missing)
  2. SELECT COUNT FROM game_players (lock with FOR UPDATE)
  3. seats >= numPlayers → LobbyFullException (409)
  4. user already seated → AlreadySeatedException (409)
  5. INSERT game_players(game_id, seats, user_id, now())  (player_idx = current seat count)
  6. if seats+1 == numPlayers → startGame(g)
  7. broadcast {event:"PLAYER_JOINED"} on /topic/games/{id}
  8. return LobbyGame
→ HTTP 200
```

`startGame(g)`: schedule hard timer, broadcast `{event:"GAME_STARTED"}`.

### 3.3 Quick-match (FIFO)

```
POST /api/matchmaking/quick {config}
→ MatchmakingService.enqueue(userId, config):
  synchronized (per-config queue):
    1. queue.add(userId)
    2. if queue.size() >= config.numPlayers:
         picked = poll N from queue
         game = lobbyService.createPublicForUsers(picked, config)
         for each user: stomp.convertAndSendToUser(uid, "/queue/match", game)
         return Optional.of(game)
       else:
         return Optional.empty()
→ HTTP 200 with {matched, game?}
```

**Critical:** clientul TREBUIE să facă SUBSCRIBE pe `/user/queue/match` ÎNAINTE de POST. Altfel pierde notificarea dacă match-ul vine în background.

### 3.4 In-game action via WebSocket

```
Client → STOMP SEND /app/games/{gameId}/actions {action: <Action>}
→ GameWsController.handleAction(gameId, action, Principal p):
  1. userId = p.userId()
  2. GameState newState = gameService.applyActionAsUser(gameId, userId, action)
     (rejects with NotSeatedException, NotYourSeatException, or GameRuleException via @MessageExceptionHandler)
  3. GameTimerService.cancel(gameId)
  4. if !newState.closed() && !newState.players().get(newState.current()).isBot():
       GameTimerService.scheduleHardTimeout(gameId, newState.current(),
         () -> autoApplyForceOnTimeout(gameId, newState.current()))
  5. broadcaster.broadcastState(gameId, newState, events):
       for each (idx, userIdAtSeat) in game_players:
         GameView v = GameView.of(newState, idx)
         stomp.convertAndSendToUser(userIdAtSeat.toString(), "/queue/games/" + gameId, {view:v, events})
```

**Per-user broadcast** (iterare prin seats) e necesar pentru a ascunde hand-urile altora. Cost N mesaje vs 1 — neglijabil pentru 2-6 players.

### 3.5 Hard timer fires

```
180s after current player's turn started, ScheduledFuture fires:
→ autoApplyForceOnTimeout(gameId, expectedPlayerIdx):
  1. Reload game
  2. state.current() != expectedPlayerIdx → no-op (already moved on)
  3. gameService.applyAction(gameId, new Action.ForceAutoAction(expectedPlayerIdx))
  4. Broadcast per-user (same as 3.4)
```

Client tries `ForceAuto` la 120s preemptiv. Server-side fallback la 180s capturează cazul când clientul e disconnect/buggy/malicious.

### 3.6 Reconnect

```
Client lost connection → auto-reconnect cu același JWT:
  STOMP CONNECT → server validates JWT
  STOMP SUBSCRIBE /user/queue/games/{gameId}
Client REST GET /api/games/{id} → primește state curent
```

Nici un state special server-side pentru "user X is connected". Engine continuă; timer hard force-uiește la 180s dacă e turul absentului.

### 3.7 Game close + cleanup

Când `applyAction` returnează `closed=true`:
1. `GameTimerService.cancel(gameId)`
2. Broadcast final per-user (cu RoundClosed event)
3. (Stage 8: update ratings here)

Row-uri `games` și `game_players` rămân pentru istoric.

## 4. Error handling

### Excepții noi

```java
LobbyNotFoundException             → 404 LOBBY_NOT_FOUND
LobbyFullException                 → 409 LOBBY_FULL
AlreadySeatedException             → 409 ALREADY_SEATED
NotSeatedException                 → 403 NOT_SEATED
NotYourSeatException               → 403 NOT_YOUR_SEAT
GameAlreadyStartedException        → 409 GAME_ALREADY_STARTED
JoinCodeNotFoundException          → 404 JOIN_CODE_NOT_FOUND
MatchmakingAlreadyQueuedException  → 409 ALREADY_QUEUED
```

Extindem `ApiExceptionHandler` cu 8 handlere noi, pattern identic cu Stage 2 (cod stabil + mesaj română).

### Erori WebSocket (STOMP)

STOMP nu are paritate cu HTTP status codes. Erorile inbound merg pe `/user/queue/errors`.

`@ControllerAdvice` global:

```java
@ControllerAdvice
public class WsExceptionHandler {
  @MessageExceptionHandler({GameRuleException.class, NotYourSeatException.class, NotSeatedException.class})
  @SendToUser("/queue/errors")
  public ApiError handle(Exception e) {
    log.warn("WS action rejected: {}", e.getMessage());
    return new ApiError(extractCode(e), e.getMessage());
  }
  @MessageExceptionHandler(Exception.class)
  @SendToUser("/queue/errors")
  public ApiError handleUnexpected(Exception e) {
    log.error("WS unexpected error", e);
    return new ApiError("INTERNAL_ERROR", "Eroare neașteptată.");
  }
}
```

**Erori la CONNECT** (JWT invalid): `MessagingException` din interceptor → STOMP întoarce ERROR frame.
**Erori la SUBSCRIBE** (user nu e seated): `AccessDeniedException` din interceptor → STOMP ERROR frame.

### Race conditions

- **Matchmaking concurrent enqueue**: `synchronized` block pe per-config queue (single-instance OK).
- **Join cu cod când game e full**: `SELECT ... FOR UPDATE` pe game row + INSERT cu PRIMARY KEY constraint pe `game_players(game_id, player_idx)`.
- **Timer race**: `autoApplyForceOnTimeout` verifică `state.current() == expectedPlayerIdx` înainte să acționeze.

### Logging

- `INFO`: game created (private/public), player joined, game started, matchmaking match made, game closed
- `DEBUG`: action received via WS, broadcast sent
- `WARN`: WS action rejected, JWT invalid pe CONNECT
- `ERROR`: WS handler exception, broadcast failed

**Niciodată în log:** JWT complet, refresh token, parolă, hand-uri.

## 5. Testing

### Piramidă

```
E2E REST + WS (Spring Test + Testcontainers)         ~12 teste
Integration (Lobby/Matchmaking/Timer services)        ~20 teste
Unit (DTOs, MatchConfig equality, code generator)     ~10 teste
```

### Unit tests

- `JoinCodeGeneratorTest`: 8 chars alfanumerice; 1000 codes have ≥999 distinct (randomness sanity)
- `MatchConfigTest`: equals/hashCode (used as Map key)
- `GameViewProjectionTest`: GameView.of(state, idx=2) ascunde mâinile celorlalți

### Integration tests

**`LobbyServiceIntegrationTest`** (~12 tests):
- createPrivate inserează corect; createPublic same
- joinByCode happy / not-found / full / already-seated / start-on-full
- listPublicWaiting filtrează corect; myGames la fel
- leave: non-owner, owner pre-start (cascade delete), post-start rejection

**`MatchmakingServiceIntegrationTest`** (~6 tests):
- single enqueue → queued; 2 enqueue (2-player config) → match; 3 enqueue → 2 match + 1 queued
- queue-uri separate per config
- cancel scoate user
- ALREADY_QUEUED dacă enqueue dublu
- Concurrent enqueue (4 threads × 2 players) → exact 2 matches, fără orphan

**`GameTimerServiceIntegrationTest`** (~4 tests):
- schedule fire la TTL configurat (test cu TTL scurt 100ms)
- cancel previne fire
- cancel pe game nemonitorizat = no-op
- multiple schedule consecutiv cancel-uiește pe primul

**`GameServiceApplyActionAsUserTest`** (~4 tests):
- user seated la idx=2 cu playerIdx=2 → OK
- user seated la idx=2 cu playerIdx=1 → NotYourSeatException
- user non-seated → NotSeatedException
- GET state pentru non-seated → NotSeatedException

### WebSocket integration tests

**`WebSocketAuthTest`** (~3 tests):
- CONNECT fără Authorization → ERROR
- CONNECT cu JWT invalid → ERROR
- CONNECT cu JWT valid → connection OK, principal populat

**`GameWsControllerIntegrationTest`** (cel mai important):
- 2 users register+verify+login + JWT
- both CONNECT + SUBSCRIBE la `/user/queue/games/{id}` + `/user/queue/errors`
- A create private + B join by code (REST)
- A trimite action via STOMP
- assert: A primește GameView cu hand-ul propriu vizibil
- assert: B primește GameView cu hand-ul lui A ascuns
- B încearcă action când nu e turul lui → primește ApiError pe `/queue/errors`; A nu primește nimic

**`MatchmakingNotificationTest`**:
- 2 users login + CONNECT + SUBSCRIBE la `/user/queue/match`
- A POST quick → `{matched:false}`
- B POST quick → `{matched:true}`
- ambii primesc același LobbyGame pe `/user/queue/match`

### E2E

**`MultiplayerE2ETest`:** full happy path 2 users — register → login → create private → join by code → both connect WS → play 5 turns → leave.

**`HardTimerE2ETest`:** override TTL la 2s, 2 users start, nici unul nu acționează, ~2s ForceAuto fires.

### Test profile additions

`application-test.yml`:
```yaml
game-timer:
  hard-timeout: PT2S
```

`application.yml`:
```yaml
game-timer:
  hard-timeout: PT3M
```

### Coverage gate

- `com.remi.engine.*` rămâne **90%**
- `com.remi.auth.*` și `com.remi.user.service.*` rămân **85%**
- `com.remi.lobby.service.*` nou: **85%**
- `com.remi.ws.*` și controllers fără gate strict (E2E le acoperă)
