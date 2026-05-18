# Stage 7 — Chat (in-game + DM)

**Date:** 2026-05-18
**Status:** Autonomous design
**Prior stage:** 6 (HEAD `59bccee`)

## Goal

1. **Match chat:** players in same match send messages visible to all match participants. Persists during match; archived after match ends.
2. **Direct messages (DM) with friends:** 1:1 private chat with anyone on your friends list.
3. Real-time delivery via STOMP; recent history via REST.

## Decisions

| Topic | Choice | Why |
|-------|--------|-----|
| Channel model | `match` channels (transient, tied to match) + `dm` channels (persistent, friends only) | Two distinct lifecycles |
| Storage | All messages persisted in `chat_messages` table; channel resolved from `channel_type` + `channel_key` | One table, flexible |
| DM channel key | `LEAST(uid_a, uid_b) || ':' || GREATEST(uid_a, uid_b)` — sorted UUID pair | Deterministic, no duplicates |
| Match channel key | `matchId.toString()` | Single value, easy |
| History limit | Last 200 messages per channel via REST | Pagination not in v1; 200 fits in a screen on demand |
| Real-time topic | `/topic/chat/match/{matchId}` for match (all participants) | Public to match only |
| DM topic | `/user/queue/dm/{otherUserId}` for sender; `/user/queue/dm/{senderUserId}` for receiver | Per-user delivery |
| Auth | Match chat: must be a participant of match. DM: must be friends + not blocked either way | Enforced server-side per send |
| Length | 1-500 chars; trim; reject empty | Reasonable for game chat |
| Rate limit | 10 msgs / 10s per user per channel | Spam protection (in-memory token bucket) |
| Block enforcement | Blocked users: DM rejected with 403; match chat allowed but receiver filters client-side (avoid breaking gameplay) | Match chat is necessary game info |
| Typing/read receipts | Out of scope v1 | YAGNI |
| Media | Text only v1 | YAGNI |
| Profanity filter | Out of scope (i18n hard) | YAGNI |

## Schema (V6)

```sql
CREATE TABLE chat_messages (
    id           BIGSERIAL PRIMARY KEY,
    channel_type VARCHAR(8) NOT NULL,  -- 'MATCH' or 'DM'
    channel_key  VARCHAR(80) NOT NULL,  -- match UUID or sorted pair "uuid:uuid"
    sender_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body         VARCHAR(500) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chat_messages_channel_type_valid CHECK (channel_type IN ('MATCH', 'DM')),
    CONSTRAINT chat_messages_body_nonempty CHECK (length(trim(body)) > 0)
);

CREATE INDEX chat_messages_channel_idx ON chat_messages(channel_type, channel_key, created_at DESC);
CREATE INDEX chat_messages_sender_idx ON chat_messages(sender_id);
```

## API surface

| Method | Path | Body | Returns | Notes |
|--------|------|------|---------|-------|
| GET | `/api/chat/match/{matchId}?limit=200` | — | `[{id, senderId, senderUsername, body, createdAt}]` | newest-last; auth: match participant |
| POST | `/api/chat/match/{matchId}` | `{body}` | `{id}` | auth: match participant; broadcasts `/topic/chat/match/{matchId}` |
| GET | `/api/chat/dm/{otherUserId}?limit=200` | — | `[{id, senderId, senderUsername, body, createdAt}]` | auth: friends + not blocked |
| POST | `/api/chat/dm/{otherUserId}` | `{body}` | `{id}` | auth: friends + not blocked; sends `/user/queue/dm/{senderId|otherId}` to both |
| GET | `/api/chat/dm/conversations` | — | `[{otherUserId, otherUsername, lastMessageAt, lastBody, unreadCount}]` | list of DM conversations (last message preview); unreadCount stays 0 in v1 (no read receipts) |

## WS topics

| Topic | Payload | When |
|-------|---------|------|
| `/topic/chat/match/{matchId}` | `{id, senderId, senderUsername, body, createdAt}` | A match message is sent |
| `/user/queue/dm/{otherUserId}` | `{id, senderId, senderUsername, body, createdAt}` | A DM message is sent (to both sender and receiver) |

## Frontend components

```
features/chat/
  match-chat-panel.component.ts   — collapsible drawer in GamePage, shows match channel
  dm-conversations.page.ts        — list of DM conversations (entry point from FriendsHomePage)
  dm-thread.page.ts               — single DM thread with input
```

NgRx feature `chat/`:
- State: `{ messagesByChannel: Record<string, ChatMessage[]>, conversations: DmConversation[] }`
- Actions: `loadMatchHistory`, `loadDmHistory`, `sendMatchMessage`, `sendDmMessage`, `messageReceived`, `loadConversations`
- Effects: REST + WS bridging
- Selectors: `selectMessages(channelKey)`, `selectConversations`

## Out of scope

- Pagination / infinite scroll
- Read receipts / typing indicators
- Group chat (non-match)
- Media (images, files, GIFs)
- Profanity filter
- Translation
- Reactions/emoji

## DoD

- [ ] V6 migration applies
- [ ] Match participants can send + receive messages in real time
- [ ] DM works between friends; blocked users get 403
- [ ] History fetches last 200 messages
- [ ] Rate limit: 11th message in 10s rejected with 429
- [ ] Frontend: match chat drawer in GamePage; DM list + thread under /friends
- [ ] Tests: 215+ backend, 155+ frontend
