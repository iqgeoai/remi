# Remi Frontend (Stage 4a)

Ionic 8 + Angular 19 + NgRx 18 client for the Remi multiplayer game backend.

This is **Stage 4a** of the build plan. Game UI is intentionally a debug placeholder (JSON state + action form). Real game UI ships in Stage 4b; mobile builds in Stage 5.

## Prereqs

- Node 20+
- Ionic CLI (`npm i -g @ionic/cli`)
- Backend running on `http://localhost:8080` (see root README)

## Run

```bash
cd frontend
npm install
npm start
```

App at http://localhost:4200. Dev proxy forwards `/api` and `/ws` to localhost:8080.

## Tests

```bash
npm test          # Karma + Jasmine, headless Chrome
```

Coverage at `coverage/index.html`.

## Smoke test (manual E2E)

See `SMOKE_TEST.md`.

## Architecture

```
src/app/
  core/        api/, ws/, auth/, models/, i18n/
  store/       NgRx (auth, lobby, match, game)
  features/
    auth/      Login, Register, VerifyEmail, RequestReset, ResetPassword
    lobby/     LobbyHome, CreateGame, JoinByCode, PublicList, QuickMatch
    game/      GamePage + 12 sub-components (Stage 4b real game UI)
  shared/      ErrorBanner, WsIndicator, GlobalErrorHandler
```

Stage 4b replaces the previous GameDebugPage placeholder. Game state is consumed
from the existing NgRx Game store; UI state (selected pieces, proposed melds,
seconds left) is local to the GamePage component via signals.

JWT stored in `localStorage` (Stage 4a); switches to Capacitor Preferences in Stage 5.
WebSocket via STOMP+SockJS at `/ws` with `Authorization: Bearer <accessToken>` on CONNECT.
