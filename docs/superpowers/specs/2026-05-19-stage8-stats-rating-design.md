# Stage 8 — Stats, History, Rating

**Date:** 2026-05-19
**Status:** Autonomous design
**Prior stage:** 7 (HEAD `145aafd`)

## Goal

1. **Match history:** every finished game recorded with final scores + winner + duration.
2. **Per-user stats:** total matches, wins/losses, win-rate, total points.
3. **ELO rating:** standard ELO (start 1000, K=32) updated at match end.
4. **Leaderboard:** top 50 by rating.
5. **Profile page:** view own + others' stats; friend profiles linked from friends list.

## Decisions

| Topic | Choice | Why |
|-------|--------|-----|
| History storage | New table `match_history` written at match end (`game_id, finished_at, duration_sec, winner_id`) + child `match_history_score(history_id, user_id, score, rank, rating_before, rating_after, rating_delta)` | Denormalised for fast querying; one history row per game |
| ELO algorithm | Standard pairwise ELO averaged for n-player games: winner gets `+K * (1 - expected_vs_avg)`, each loser gets `-K * (expected_vs_avg) / (n-1)` | Simple, correct in expectation, well-understood |
| K-factor | 32 (chess standard for beginners/intermediate) | Reasonable volatility |
| Starting rating | 1000 | Standard ELO baseline |
| Rating storage | Add `rating INT NOT NULL DEFAULT 1000` to `users` table (V7 migration) | Single value per user; history rows track historical |
| Stats recompute | Aggregated on-demand from `match_history_score` (no denormalised cache); add indexes | Avoids cache invalidation; ~10ms for 10k matches |
| Leaderboard | `GET /api/leaderboard?limit=50` returns users ordered by rating DESC | Indexed on rating |
| Profile | `GET /api/users/{id}/profile` returns `{username, rating, stats, recentMatches}` | Single endpoint for profile page |
| Trigger | `MatchService.finishMatch(matchId, winnerId, finalScores)` writes history + computes ELO + persists rating updates atomically (single @Transactional method) | Atomic; ELO consistent with history |
| Anti-abuse | None v1 (no rating decay, no concession penalty) | YAGNI |

## Schema (V7)

```sql
ALTER TABLE users ADD COLUMN rating INT NOT NULL DEFAULT 1000;
CREATE INDEX users_rating_idx ON users(rating DESC);

CREATE TABLE match_history (
    id            BIGSERIAL PRIMARY KEY,
    game_id       UUID NOT NULL UNIQUE REFERENCES games(id) ON DELETE CASCADE,
    finished_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    duration_sec  INT NOT NULL,
    winner_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX match_history_winner_idx ON match_history(winner_id);
CREATE INDEX match_history_finished_at_idx ON match_history(finished_at DESC);

CREATE TABLE match_history_score (
    id             BIGSERIAL PRIMARY KEY,
    history_id     BIGINT NOT NULL REFERENCES match_history(id) ON DELETE CASCADE,
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    score          INT NOT NULL,
    rank           INT NOT NULL,
    rating_before  INT NOT NULL,
    rating_after   INT NOT NULL,
    rating_delta   INT NOT NULL,
    UNIQUE (history_id, user_id)
);

CREATE INDEX match_history_score_user_idx ON match_history_score(user_id);
```

Note: `games` table is the existing match storage (`MatchRepository`/`GameRepository` from prior stages). Adapt FK name to actual table.

## API surface

| Method | Path | Returns | Notes |
|--------|------|---------|-------|
| GET | `/api/users/{id}/profile` | `{id, username, rating, totalMatches, wins, losses, winRate, totalPoints, recentMatches:[{gameId, finishedAt, durationSec, rank, score, ratingDelta, winnerUsername}]}` | last 20 matches |
| GET | `/api/leaderboard?limit=50` | `[{id, username, rating, totalMatches, wins}]` | descending rating |
| GET | `/api/users/me/stats` | same shape as profile | shortcut for current user |

## Frontend

```
features/stats/
  profile.page.ts          — /profile/:id
  my-stats.page.ts         — /me (or routes to profile/:meId)
  leaderboard.page.ts      — /leaderboard
```

NgRx feature `stats/` (small):
- State: `{ profiles: Record<userId, Profile>, leaderboard: LeaderboardEntry[] }`
- Actions: `loadProfile(id)`, `profileLoaded`, `loadLeaderboard`, `leaderboardLoaded`
- Effects: REST

Navigation entry points:
- LobbyHomePage: "Statistici" + "Leaderboard" buttons
- FriendsListItem: tap → friend profile
- AppComponent header: avatar/username → own profile

## Out of scope

- Per-game move history (only summary)
- Replay/spectate
- Achievements / badges
- Seasons / rating reset
- Streaks
- Detailed analytics (avg pieces per turn, etc.)

## DoD

- [ ] V7 migration applies
- [ ] Match end writes history + updates ratings (verified by test)
- [ ] ELO: known fixtures (1000 vs 1000, winner gains +16, loser -16) — testable
- [ ] Leaderboard returns top 50
- [ ] Profile endpoint returns stats + recent matches
- [ ] Frontend: Profile page, Leaderboard page, link from lobby + friends list
- [ ] Tests: 218+ backend, 152+ frontend
