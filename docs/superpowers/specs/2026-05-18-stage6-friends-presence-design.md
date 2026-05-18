# Stage 6 — Friends + Presence

**Date:** 2026-05-18
**Status:** Autonomous design (user waived approval gates)
**Prior stage:** 5b (HEAD `8297329`)

## Goal

1. **Friend requests:** search users by username, send/accept/reject/cancel requests.
2. **Friend list with live presence:** online/offline dot updates in real time via WebSocket.
3. **Block:** block a user → blocks future requests and (later in 7) chat.
4. **Invite friend to private match:** one-tap invite via deep link / WebSocket push.

## Decisions

| Topic | Choice | Why |
|-------|--------|-----|
| Friendship model | Single row `(requester_id, addressee_id, status, created_at, accepted_at)` UNIQUE on the ordered pair | Symmetric query via two-direction `WHERE`; status enum: `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED` |
| Block model | Separate table `user_blocks(blocker_id, blocked_id)` UNIQUE — bidirectional read but unidirectional write | Standard pattern; allows asymmetric "I blocked them but they didn't block me" |
| Presence | In-memory `PresenceRegistry` keyed by user UUID with last-seen timestamp; populated on WS connect, cleared on disconnect; cluster-friendly redesign deferred | App is single-instance for now; Redis pubsub for multi-node later |
| Presence broadcast | STOMP topic `/user/{userId}/queue/presence` — when X comes online/offline, fan out to X's friends only | Hides global presence; each friend gets per-friend updates |
| Friend search | `GET /api/users/search?q=<prefix>` returns up to 20 users matching `username_normalized LIKE 'prefix%'` | Server limit prevents enumeration; case-insensitive prefix match |
| Invite to game | `POST /api/friends/{friendId}/invite` creates a private match (existing lobby code mechanism) + sends WS push `{type:friend-invite, code, matchId}` to friend's `/user/queue/invites` | Reuses existing private-match join-by-code flow; deep link from push tap |
| Frontend store | NgRx feature `friends/` with selectors `selectFriends`, `selectPendingIncoming`, `selectPendingOutgoing`, `selectIsOnline(userId)` | Same pattern as existing auth/lobby/game features |

## Schema (V5 migration)

```sql
CREATE TYPE friendship_status AS ENUM ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED');

CREATE TABLE friendships (
    id           BIGSERIAL PRIMARY KEY,
    requester_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    addressee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status       friendship_status NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at  TIMESTAMPTZ,
    CONSTRAINT friendships_distinct CHECK (requester_id <> addressee_id),
    CONSTRAINT friendships_unique_pair UNIQUE (requester_id, addressee_id)
);

CREATE INDEX friendships_requester_idx ON friendships(requester_id);
CREATE INDEX friendships_addressee_idx ON friendships(addressee_id);
CREATE INDEX friendships_status_accepted_idx ON friendships(status) WHERE status = 'ACCEPTED';

CREATE TABLE user_blocks (
    id         BIGSERIAL PRIMARY KEY,
    blocker_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT user_blocks_distinct CHECK (blocker_id <> blocked_id),
    CONSTRAINT user_blocks_unique UNIQUE (blocker_id, blocked_id)
);

CREATE INDEX user_blocks_blocker_idx ON user_blocks(blocker_id);
```

## API surface

| Method | Path | Body | Returns | Notes |
|--------|------|------|---------|-------|
| GET | `/api/users/search?q=<prefix>` | — | `[{id, username}]` max 20 | excludes self + blocked + already-friended |
| GET | `/api/friends` | — | `[{id, username, online, since}]` | accepted friendships, presence joined |
| POST | `/api/friends/requests` | `{addresseeId}` | 201 created | rejects if blocked either way |
| GET | `/api/friends/requests` | — | `{incoming:[], outgoing:[]}` | both PENDING |
| POST | `/api/friends/requests/{id}/accept` | — | 204 | only addressee can accept |
| POST | `/api/friends/requests/{id}/reject` | — | 204 | only addressee can reject |
| DELETE | `/api/friends/requests/{id}` | — | 204 | requester cancels |
| DELETE | `/api/friends/{friendId}` | — | 204 | unfriend |
| POST | `/api/users/{userId}/block` | — | 204 | also removes friendship if exists |
| DELETE | `/api/users/{userId}/block` | — | 204 | unblock |
| GET | `/api/users/blocked` | — | `[{id, username}]` | |
| POST | `/api/friends/{friendId}/invite` | `{format, jokerMode, scoreLimit}` (match settings) | `{code, matchId}` | creates private match + pushes |

## WebSocket topics

| Topic | Payload | When |
|-------|---------|------|
| `/user/queue/presence` | `{userId, online, since?}` | A friend comes online/offline |
| `/user/queue/invites` | `{type:friend-invite, fromUserId, code, matchId, inviterUsername}` | A friend invites you |
| `/user/queue/friend-requests` | `{type, requestId, fromUserId, fromUsername}` (type: incoming, accepted, rejected) | Friend request events |

## Frontend components

```
features/friends/
  friends-home.page.ts         — tab with: friends list, search button, requests badge
  friends-list.component.ts    — list of friends with online dot + invite button
  friend-search.page.ts        — search + send-request flow
  friend-requests.page.ts      — incoming/outgoing tabs
  blocked-list.page.ts         — blocked users with unblock action
```

NgRx feature `friends/`:
- State: `{friends, pendingIn, pendingOut, blocked, search}` + `presenceMap: Record<userId, boolean>`
- Actions: `loadFriends`, `searchUsers`, `sendRequest`, `acceptRequest`, `rejectRequest`, `cancelRequest`, `unfriend`, `block`, `unblock`, `inviteToGame`
- Effects: call API + handle WS push events
- Selectors: per-friend `isOnline`

## Out of scope

- Avatars (no avatar storage in V5; add later)
- Activity feed ("X is playing Remi now")
- Friend recommendations
- Group chat / clans
- Search by email (privacy)
- Friend leaderboards (Stage 8)

## Testing

- **Backend:** 12-15 tests covering repo, service, controllers + WS push behavior
- **Frontend:** 15-20 tests covering selectors, effects, components
- Existing 196 backend tests + 147 frontend tests must stay green

## DoD

- [ ] V5 migration applies in Testcontainers
- [ ] Search returns matches, excludes self + blocked
- [ ] Request lifecycle works (send/accept/reject/cancel/unfriend) with proper auth checks
- [ ] Block prevents request creation; existing friendship deleted on block
- [ ] Presence: friend coming online triggers WS push to other friends only
- [ ] Friend invite creates private match + pushes invite to friend
- [ ] Frontend: friends tab in lobby; search; requests; presence dots update live
- [ ] Tests: 208+ backend, 165+ frontend
