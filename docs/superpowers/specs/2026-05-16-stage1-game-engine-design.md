# Etapa 1 — Game Engine pe Backend (Design)

**Data:** 2026-05-16
**Status:** Approved
**Scope:** Etapa 1 din planul multi-etape pentru platforma Remi multiplayer. Vezi `README` sau context conversație pentru întregul roadmap (Etape 1-8).

## Context

Repository-ul conține `assets/remi.html` — implementare single-player a jocului Remi românesc (varianta cu piese), ~3140 linii într-un singur HTML. Joc contra AI, ruleaza în browser. Acest spec descrie portarea logicii de joc într-un backend Java/Spring Boot care va fi fundația pentru etapele ulterioare (multiplayer, auth, social).

Etapa 1 **nu** include: WebSocket, autentificare utilizatori, lobby/matchmaking, chat, frontend Ionic. Doar engine + persistență + endpoints REST de development.

## Cerințe (confirmate cu user)

- **Moduri:** ambele — `ETALAT` (clasic) și `TABLA` (închidere într-o singură mutare)
- **AI:** port direct al AI-ului din `assets/remi.html` (paritate funcțională)
- **Livrabil:** librărie engine + persistență Postgres + dev REST endpoints (jucabil local cu curl)
- **Arhitectură:** hibrid — service Spring stateful la suprafață, engine pur funcțional dedesubt, persistență `Game(id, state JSONB)`

## Stack tehnic

- Java 21 LTS
- Spring Boot 3.x
- Maven (single module)
- PostgreSQL 16 (Testcontainers pentru integration tests)
- Flyway pentru migrații
- Jackson (cu suport records nativ)
- JUnit 5 + AssertJ pentru unit tests
- jqwik pentru property-based tests
- JaCoCo pentru coverage; PIT pentru mutation testing (engine only)

## 1. Arhitectură

Single module Maven, separare prin packages cu reguli stricte de dependență:

```
com.remi.engine        ← pur funcțional, ZERO dependențe Spring/JPA
  .domain              ← records: Piece, Player, GameState, Meld, Action, ActionResult
  .rules               ← MeldValidator, Scoring, Dealer, GameEngine
  .ai                  ← Bot (port aiPlay), MeldFinder
com.remi.persistence   ← JPA: GameEntity(id, state JSONB, version, timestamps), GameRepository
com.remi.service       ← GameService — orchestrare load → engine.apply → save
com.remi.api           ← REST controllers (Etapa 1: doar dev endpoints)
com.remi.config        ← Spring config, Jackson, Flyway
```

**Regula cheie:** packageul `engine` nu importă nimic din Spring sau JPA. Poate fi rulat de mână (`main()`), fără container. Asta îl face testabil rapid și reutilizabil pentru bot training, replay, etc.

## 2. Componente

### 2.1 Domain (records imutabile)

```java
public enum Color { RED, YELLOW, BLUE, BLACK, JOKER }
public enum MeldType { GROUP, SUITE }
public enum Phase { DRAW, ACTION, DISCARD }
public enum Mode { ETALAT, TABLA }
public enum DrawSource { STOCK, DISCARD }
public enum Difficulty { EASY, MED, HARD }

public record Piece(int id, int num, Color color, boolean isJoker) {}
public record Meld(int owner, MeldType type, List<Piece> pieces,
                   Map<Integer, Integer> placedBy) {}
public record Player(String name, boolean isBot, List<Piece> hand,
                     boolean hasEtalat, boolean calledAtu, boolean announced,
                     Integer mustUsePieceId) {}
public record GameState(UUID id, List<Player> players, List<Piece> stock,
                        List<Piece> discard, Piece atu, List<Meld> melds,
                        int current, Phase phase, DrawSource drewFrom,
                        int turnTaken, int round, Mode mode,
                        Difficulty difficulty, boolean doubleGame,
                        boolean closed, List<Integer> totals, long seed) {}
```

`placedBy` în `Meld` ține minte ce piesă a fost pusă de cine (pentru scoring când layoff pe meld-ul altcuiva). În JS original `placedBy` era câmp pe `piece`; aici e `Map` separat pe meld ca să păstrăm `Piece` ca valoare canonică imutabilă (același piece poate fi referit în mai multe locuri fără ambiguitate).

### 2.2 Action — sealed interface

```java
public sealed interface Action {
  record DrawFromStock(int playerIdx) implements Action {}
  record TakeDiscard(int playerIdx, int discardIdx) implements Action {}
  record Etalat(int playerIdx, List<MeldProposal> melds) implements Action {}
  record Layoff(int playerIdx, List<LayoffProposal> layoffs) implements Action {}
  record Discard(int playerIdx, int pieceId) implements Action {}
  record ForceAutoAction(int playerIdx) implements Action {}
}
public record MeldProposal(MeldType type, List<Integer> pieceIds) {}
public record LayoffProposal(int pieceId, int meldIdx) {}
```

### 2.3 ActionResult — sealed

```java
public sealed interface ActionResult {
  record Accepted(GameState newState, List<DomainEvent> events) implements ActionResult {}
  record Rejected(RejectReason code, String message) implements ActionResult {}
}

public enum RejectReason {
  NOT_YOUR_TURN, WRONG_PHASE, INVALID_MELD, FIRST_MELD_TOO_FEW_POINTS,
  FIRST_MELD_NEEDS_SUITE, MUST_USE_TAKEN_PIECE, CANNOT_TAKE_OPENING_PIECE,
  CANNOT_BREAK_LINE, NOT_ETALAT, PIECE_NOT_IN_HAND, GAME_CLOSED,
  HAND_TOO_FULL_TO_DISCARD, INVALID_LAYOFF, STOCK_EMPTY, DISCARD_EMPTY
}

public sealed interface DomainEvent {
  record TurnStarted(int playerIdx) implements DomainEvent {}
  record CardDrawn(int playerIdx, DrawSource from) implements DomainEvent {}
  record PieceDiscarded(int playerIdx, int pieceId) implements DomainEvent {}
  record PlayerEtalat(int playerIdx, int totalPoints) implements DomainEvent {}
  record LayoffPlayed(int playerIdx, int meldIdx, int pieceId) implements DomainEvent {}
  record RoundClosed(int closerIdx, List<RoundResult> results, boolean withJoker) implements DomainEvent {}
}
```

`DomainEvent` are dublu rol: notificare frontend (animații) și auditare/chat-sistem.

### 2.4 Engine API (pur, fără Spring)

```java
public final class GameEngine {
  public static ActionResult apply(GameState state, Action action);
}
public final class Dealer {
  public static GameState deal(int numPlayers, Mode mode, Difficulty diff, long seed);
}
public final class MeldValidator {
  public static boolean isValid(Meld meld);
}
public final class Scoring {
  public static int finalPieceValue(Piece p);
  public static int firstMeldPieceValue(Piece p, Meld m);
  public static Integer inferJokerNumber(Meld m, Piece joker);
  public static List<RoundResult> closeRound(GameState s, int closerIdx, Piece lastDiscarded);
}
```

### 2.5 AI

```java
public final class Bot {
  public static Action decide(GameState state, int playerIdx);
}
```

Port direct al `aiPlay()` din JS, descompus în decizii per-acțiune: bot-ul cere o acțiune la un moment dat. Service-ul îl apelează în buclă până ajunge la un jucător uman sau se închide runda.

### 2.6 Persistență

```java
@Entity @Table(name="games")
class GameEntity {
  @Id UUID id;
  @JdbcTypeCode(SqlTypes.JSON) GameState state;
  @Version Long version;
  Instant createdAt, updatedAt;
}
interface GameRepository extends JpaRepository<GameEntity, UUID> {}
```

### 2.7 Service

```java
@Service class GameService {
  GameState create(int numPlayers, Mode mode, Difficulty diff, Long seed);
  GameState applyAction(UUID gameId, Action action);
  GameState runBotsUntilHuman(UUID gameId);
  GameState get(UUID gameId);
}
```

### 2.8 REST API (dev-only)

```
POST   /api/dev/games              → create joc
GET    /api/dev/games/{id}         → state curent (proiectat per player)
POST   /api/dev/games/{id}/actions → applyAction
POST   /api/dev/games/{id}/bot     → runBotsUntilHuman
```

În Etapa 3, aceste endpoint-uri vor fi înlocuite/extinse cu WebSocket + auth.

## 3. Data flow

### 3.1 Creare joc

1. `POST /api/dev/games {numPlayers:3, mode:"ETALAT", difficulty:"MED", seed?:42}`
2. `GameService.create` → `Dealer.deal(...)` → `GameState` imutabil
3. `repo.save(new GameEntity(id, state, version=0))`
4. HTTP 201 + `GameView` (proiecție per-player: hand-ul propriu vizibil, ale celorlalți doar count; restul state-ului — stock count, discard, melds, atu, current, phase, scores — vizibile)

### 3.2 Acțiune umană

1. `POST /api/dev/games/{id}/actions` cu body Action
2. Controller deserializează polimorfic (Jackson `@JsonTypeInfo` pe sealed)
3. `GameService.applyAction`:
   - load entity (404 dacă lipsește)
   - `GameEngine.apply(state, action)`
   - dacă `Rejected` → throw `GameRuleException` → HTTP 400
   - dacă `Accepted` → `entity.state = newState`, `repo.save` (JPA `@Version` → 409 la race)
   - return `ApplyResponse(view, events)`

### 3.3 Avans AI (buclă până la uman)

1. `POST /api/dev/games/{id}/bot`
2. `runBotsUntilHuman`:
   ```
   while (!state.closed && state.players[current].isBot && steps < 50):
     action = Bot.decide(state, current)
     state = ((Accepted) GameEngine.apply(state, action)).newState
     events.addAll(...)
     steps++
   if (steps == 50) throw IllegalEngineStateException
   ```
3. Save și return.

### 3.4 Determinism

- `Dealer.deal(..., seed)` folosește `new Random(seed)`. Default = `System.nanoTime()` în prod, fix în teste.
- `Bot.decide` e pur determinist — niciun random intern. Dat fiind un state, decizia e mereu aceeași.

### 3.5 Concurrency

JPA `@Version` optimistic lock. La conflict → HTTP 409. Etapa 1 nu are WebSocket / push.

### 3.6 Schema DB

```sql
-- V1__init_games.sql
CREATE TABLE games (
  id          UUID PRIMARY KEY,
  state       JSONB NOT NULL,
  version     BIGINT NOT NULL DEFAULT 0,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX games_updated_at_idx ON games(updated_at);
```

## 4. Error handling

### Două clase de erori

**1. Erori de domeniu** — *valori de retur*, nu excepții.
Engine returnează `Rejected(RejectReason, message)`. Mesajele sunt în română. Sealed switch obligă tratament exhaustiv.

În service, `Rejected` devine `GameRuleException(code, message)` ← unchecked, marker "input invalid".

**2. Erori sistem** — *excepții*.
- `GameNotFoundException` → 404
- `OptimisticLockingFailureException` → 409
- `IllegalEngineStateException` → 500 + log ERROR (bug nostru)
- `JsonProcessingException` (deserializare action) → 400

### Global exception handler

```java
@RestControllerAdvice
class ApiExceptionHandler {
  @ExceptionHandler(GameRuleException.class)
  ResponseEntity<ApiError> ruleViolation(GameRuleException e) { /* 400 */ }
  @ExceptionHandler(GameNotFoundException.class) /* 404 */
  @ExceptionHandler(OptimisticLockingFailureException.class) /* 409 */
  @ExceptionHandler(MethodArgumentNotValidException.class) /* 400 */
  @ExceptionHandler(Exception.class) /* 500 + log ERROR */
}
public record ApiError(String code, String message) {}
```

### Validare în engine (defense in depth)

Toate validările sunt în engine, nu în controller. Controllerul doar deserializează și apelează. Engine verifică:
- `action.playerIdx == state.current` (NOT_YOUR_TURN)
- `state.phase` permite acțiunea (WRONG_PHASE)
- `state.closed == false` (GAME_CLOSED)
- Piese referite există în mâna jucătorului (PIECE_NOT_IN_HAND)
- `mustUsePieceId` respectat (MUST_USE_TAKEN_PIECE)
- Reguli specifice (etalat ≥45p, suite/terta-de-1, break-line rules, etc.)

Service-ul nu validează — singura sursă de adevăr pentru reguli e engine-ul.

### Force auto action

`Action.ForceAutoAction` definit deja pentru Etapa 3+ (timer expirat). Engine rezolvă identic ca JS: dacă phase=DRAW → draw stock; dacă phase=ACTION/DISCARD → auto-discard piesa cea mai valoroasă non-joker non-mustUse.

### Logging

- `INFO`: create/close game
- `DEBUG`: acțiune acceptată (gameId, action.type, playerIdx)
- `WARN`: `Rejected` în prod
- `ERROR`: excepții sistem
- Nu se logează `state` complet (debug se face cu replay).

## 5. Testing

### Piramidă

```
E2E REST (MockMvc)              ~5-10 teste
Integration (Service + TC PG)   ~20 teste
Unit (engine + AI, pure)        ~200+ teste
```

### Unit tests pe engine

**Per regulă, nu per clasă.** Exemple:

- `MeldValidatorTest`: group valid/invalid, suite cu wrap 12-13-1, jokerii ≤ realii, ≥3 piese, max 4 într-un group, etc. (~40 cases)
- `ScoringTest`: finalPieceValue mapping; firstMeldPieceValue position-aware pentru 1 și jokerii; inferJokerNumber în suite cu anchor + diff + wrap; closeRound cu bonus închidere +50, atu +50, fără etalat -100, joker close ×2, joc dublu ×2; tabla 250/500 doubled.
- `DealerTest`: 2-6 players → 15/14 hand sizes, atu setat, doubleGame când atu=1 sau joker, calledAtu pe cei cu match, seed determinist.

**Acceptance tests** — scenarii din JS re-jucate:
- `firstMeldRequiresSuiteOr1Terta`
- `cannotDiscardWithoutUsingMustUsePiece`
- `cannotTakeOpeningDiscardPiece`
- `breakingLineRequiresEtalatAnd4Pieces`
- `closingWithJokerDoublesPoints`
- `notEtalatPenaltyIs100`
- (~30 scenarii)

**Property-based (jqwik):**
- `validMeldStaysValidUnderShuffle` (numai pentru GROUP — SUITE e order-sensitive)
- `dealerAlwaysProducesValidState` pentru 2-6 players × seed-uri arbitrare → tot ce iese e consistent (106 piese în total, hand sizes corecte)

### AI tests

- Unit: `drawFromStockWhenPhaseIsDraw`, `etalatsWhenFirstMeldSetExists`, `layoffsAfterEtalat`, `discardsHighestValueNonJoker`, `closesInTablaModeWhenHandFits`
- **Golden playthrough**: `fullGameSeed42_3Bots_endsConsistently` — joc complet AI vs AI cu seed fix, salvăm totaluri la primul rulaj, asertăm că nu se schimbă. Cel mai important test de regresie.

### Integration tests

**Testcontainers PostgreSQL** (nu H2 — JSONB ne trebuie real Postgres).
- `GameRepositoryIT`: round-trip JSONB, optimistic lock pe update concurent, migrațiile Flyway aplică
- `GameServiceIT`: create + apply turn, runBots oprește la player 0, `Rejected` nu mutează DB-ul (critic — asertăm că nicio scriere nu trece)

### E2E REST

`@SpringBootTest @AutoConfigureMockMvc @Testcontainers`:
- `completeGameAgainstBots_seed42`: create joc, alternează POST `/bot` + POST `/actions` până la închidere, verifică state final via GET.

### Coverage

- JaCoCo (obligatoriu, gate în build): 90% pe `com.remi.engine.**`, 80% pe service și AI, 60% pe API
- PIT (mutation testing, recomandat, nu blocant): 75% kill rate pe engine. Configurat dar nu rulat în CI default — rulat manual sau în nightly.

### Test data builders

`PieceBuilder`, `MeldBuilder`, `GameStateBuilder` cu defaults rezonabile — esențial pentru lizibilitate.

```java
GameState s = aGame().withPlayers("Tu", "Bot1").withAtu(piece(5, RED))
                     .with(player(0).holding(piece(1,RED), piece(1,BLUE), ...))
                     .build();
```

## Roadmap (context, nu scope al Etapei 1)

1. **Etapa 1 — Game engine + persistență + dev REST** ← acest spec
2. Etapa 2 — Auth + DB schema utilizatori
3. Etapa 3 — Multiplayer real-time (WebSocket) + lobby
4. Etapa 4 — Frontend Ionic/Angular
5. Etapa 5 — Build mobile (iOS + Android via Capacitor)
6. Etapa 6 — Friend list + presence
7. Etapa 7 — Chat in-game + DM
8. Etapa 8 — Stats, istoric (event log), rating

Engine-ul pur funcțional din Etapa 1 este ales special ca să facă etapele 3 (replay/reconnect), 5 (offline mode), și 8 (event log retroactiv) fezabile fără rewrite.
