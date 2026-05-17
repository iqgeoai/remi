# Remi Backend — Stage 1: Game Engine

Pure-functional Java game engine for Romanian Remi (tile variant), wrapped by Spring Boot service with PostgreSQL JSONB snapshot persistence and dev REST endpoints.

This is **Stage 1** of an 8-stage build plan; see `docs/superpowers/specs/2026-05-16-stage1-game-engine-design.md` for the design and `docs/superpowers/plans/2026-05-16-stage1-game-engine*.md` for the implementation plan.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker (for tests and local Postgres)

## Run locally

Start a Postgres container:

```bash
docker run -d --name remi-pg -p 5432:5432 \
  -e POSTGRES_USER=remi -e POSTGRES_PASSWORD=remi -e POSTGRES_DB=remi \
  postgres:16-alpine
```

Run the app:

```bash
mvn spring-boot:run
```

The app listens on `http://localhost:8080`. Flyway applies the `V1__init_games.sql` migration on first boot.

## Try the API

```bash
# Create a 3-player game (1 human + 2 bots, seed 42 for reproducibility)
curl -X POST http://localhost:8080/api/dev/games \
  -H 'Content-Type: application/json' \
  -d '{"numPlayers":3,"mode":"ETALAT","difficulty":"MED","seed":42}'

# Get current state (use the id returned above; ?viewer=0 hides other players' hands)
curl http://localhost:8080/api/dev/games/<id>?viewer=0

# Discard a piece (player 0 starts in DISCARD phase with 15 pieces)
curl -X POST http://localhost:8080/api/dev/games/<id>/actions \
  -H 'Content-Type: application/json' \
  -d '{"type":"DISCARD","playerIdx":0,"pieceId":<piece_id>}'

# Let bots take their turns until it's your turn again
curl -X POST http://localhost:8080/api/dev/games/<id>/bot
```

### Available actions

All actions are sent to `POST /api/dev/games/{id}/actions` with a JSON body whose `type` discriminates the variant:

| `type`            | Required fields                                     | Notes                                                                                  |
| ----------------- | --------------------------------------------------- | -------------------------------------------------------------------------------------- |
| `DRAW_FROM_STOCK` | `playerIdx`                                         | Pick up the top of the stock pile (phase=DRAW)                                         |
| `TAKE_DISCARD`    | `playerIdx`, `discardIdx`                           | Take from the discard pile; `discardIdx=last` is the top                                |
| `ETALAT`          | `playerIdx`, `melds: [{type, pieceIds}]`            | Lay down meld(s); first meld must total ≥45p and include a suite or terta-de-1         |
| `LAYOFF`          | `playerIdx`, `layoffs: [{pieceId, meldIdx}]`        | Add piece(s) to existing meld(s); requires etalat                                       |
| `DISCARD`         | `playerIdx`, `pieceId`                              | Throw to discard pile and end turn                                                     |
| `FORCE_AUTO`      | `playerIdx`                                         | Used when turn timer expires; auto-draws or auto-discards                              |

Error responses use `{"code": "REASON", "message": "..."}` with HTTP 400 (rule violation), 404 (game not found), 409 (optimistic lock conflict), or 500 (engine bug).

## Tests

```bash
mvn test     # all unit tests (no Docker needed)
mvn verify   # all tests including ITs (Testcontainers Postgres) + 90% engine coverage gate
```

Integration tests use Testcontainers — Docker must be running.

## Architecture (Stage 1 only)

```
com.remi.engine        ← pure functional, ZERO Spring/JPA imports
  .domain              ← records: Piece, Player, GameState, Meld, Action, ActionResult
  .rules               ← MeldValidator, Scoring, Dealer, GameEngine
  .ai                  ← Bot (port of aiPlay), MeldFinder
com.remi.persistence   ← JPA: GameEntity(id, state JSONB, version, timestamps)
com.remi.service       ← GameService — orchestration: load → engine.apply → save
com.remi.api           ← REST controllers (dev-only in Stage 1)
com.remi.config        ← Spring config, Jackson polymorphic mixins
```

The engine is deliberately Spring-free so it can be reused later (replay/event log, bot training, offline mode in mobile builds).

## Auth (Stage 2)

Stage 2 adds user accounts. Required env vars for prod (SMTP):

```bash
export JWT_SECRET="your-256-bit-secret-rotate-on-compromise"
export SMTP_HOST=smtp.mailtrap.io
export SMTP_PORT=587
export SMTP_USER=...
export SMTP_PASS=...
export MAIL_FROM=noreply@remi.example
export MAIL_VERIFICATION_LINK_BASE=https://app.remi.example/verify
export MAIL_RESET_LINK_BASE=https://app.remi.example/reset
```

### Try the auth flow

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","username":"alice","password":"passwordxx"}'

# (Check email for verification token, then:)
curl -X POST http://localhost:8080/api/auth/verify-email \
  -H 'Content-Type: application/json' \
  -d '{"token":"<token-from-email>"}'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"emailOrUsername":"alice","password":"passwordxx"}'
# → {"accessToken":"...","refreshToken":"...","accessExpiresAt":"..."}

# Authenticated request
curl http://localhost:8080/api/users/me -H "Authorization: Bearer <accessToken>"

# Refresh
curl -X POST http://localhost:8080/api/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<refreshToken>"}'

# Logout
curl -X POST http://localhost:8080/api/auth/logout \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<refreshToken>"}'
```

The Stage 1 `/api/dev/games/*` endpoints remain accessible without auth (whitelist in `SecurityConfig`). They will be replaced by authenticated `/api/games/*` in Stage 3.

## Source of truth for game rules

`assets/remi.html` — the original single-player HTML implementation. When in doubt, the JS implementation wins. The Java port is faithful (modulo two bug fixes caught by jqwik properties: `findLayoffs` capacity tracking, and `closeRound` when etalat/layoff empties the hand).
