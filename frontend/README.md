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
  store/       NgRx (auth, lobby, match, game, friends)
  features/
    auth/      Login, Register, VerifyEmail, RequestReset, ResetPassword
    lobby/     LobbyHome, CreateGame, JoinByCode, PublicList, QuickMatch
    friends/   FriendsHome, FriendSearch, FriendRequests, BlockedList
    game/      GamePage + 12 sub-components (Stage 4b real game UI)
  shared/      ErrorBanner, WsIndicator, GlobalErrorHandler
```

## Friends + presence (Stage 6)

The friends feature lives under `/friends`. From the lobby home there is a
Prieteni card that opens the hub page with three sub-routes:

- `/friends/search` — debounced username search, send friend request
- `/friends/requests` — incoming/outgoing pending requests (accept/refuse/cancel)
- `/friends/blocked` — manage blocked users

Backend persists friendships + blocks in the `friendships` / `user_blocks`
tables (V5 migration). REST under `/api/friends` and `/api/users/{id}/block`.

**Presence** is in-memory on the server: every WS connect/disconnect updates
`PresenceRegistry` and the server pushes per-friend deltas on the
`/user/queue/presence` topic. `FriendsWsBridge` reduces these messages into
the `friends` NgRx slice, so the UI's online/offline label updates live.

**Invites** are powered by `POST /api/friends/{friendId}/invite`, which
creates a private match owned by the inviter and pushes a `friend-invite`
message on the friend's `/user/queue/invites` topic. The bridge auto-joins
the invited user via the existing join-by-code flow, so both players land
in `/game/{matchId}` without any extra prompts.

Stage 4b replaces the previous GameDebugPage placeholder. Game state is consumed
from the existing NgRx Game store; UI state (selected pieces, proposed melds,
seconds left) is local to the GamePage component via signals.

JWT stored in `localStorage` (Stage 4a); switches to Capacitor Preferences in Stage 5.
WebSocket via STOMP+SockJS at `/ws` with `Authorization: Bearer <accessToken>` on CONNECT.

## Running on mobile

See [`docs/MOBILE_DEV.md`](../docs/MOBILE_DEV.md) for prereqs, first-time setup, daily workflow, and troubleshooting for iOS Simulator + Android Emulator targets.
