# Stage 8 — Stats / History / Rating Implementation Plan

> **For agentic workers:** Subagent-driven execution.

**Spec:** `docs/superpowers/specs/2026-05-19-stage8-stats-rating-design.md`

---

## Phase A — Backend persistence

### Task A1: V7 migration

**File:** `src/main/resources/db/migration/V7__stats_rating.sql`

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

**IMPORTANT:** The FK `REFERENCES games(id)` assumes the games table is named `games`. Verify with `grep "CREATE TABLE games" src/main/resources/db/migration/`. If the table is named differently (e.g. `game`, `match`), adjust the FK accordingly. Same for any FK column mismatches.

- [ ] Verify: `mvn test 2>&1 | tail -10` BUILD SUCCESS
- [ ] Commit: `git add src/main/resources/db/migration/V7__stats_rating.sql && git commit -m "feat(stats): V7 migration rating + match_history + match_history_score"`

### Task A2: Entities + repos

**Files (under `src/main/java/com/remi/stats/`):**

`MatchHistory.java`:
```java
package com.remi.stats;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_history")
public class MatchHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false, unique = true)
    private UUID gameId;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt = Instant.now();

    @Column(name = "duration_sec", nullable = false)
    private int durationSec;

    @Column(name = "winner_id", nullable = false)
    private UUID winnerId;

    protected MatchHistory() {}

    public MatchHistory(UUID gameId, int durationSec, UUID winnerId) {
        this.gameId = gameId;
        this.durationSec = durationSec;
        this.winnerId = winnerId;
    }

    public Long getId() { return id; }
    public UUID getGameId() { return gameId; }
    public Instant getFinishedAt() { return finishedAt; }
    public int getDurationSec() { return durationSec; }
    public UUID getWinnerId() { return winnerId; }
}
```

`MatchHistoryScore.java`:
```java
package com.remi.stats;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "match_history_score", uniqueConstraints = @UniqueConstraint(columnNames = {"history_id", "user_id"}))
public class MatchHistoryScore {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "history_id", nullable = false)
    private Long historyId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private int rank;

    @Column(name = "rating_before", nullable = false)
    private int ratingBefore;

    @Column(name = "rating_after", nullable = false)
    private int ratingAfter;

    @Column(name = "rating_delta", nullable = false)
    private int ratingDelta;

    protected MatchHistoryScore() {}

    public MatchHistoryScore(Long historyId, UUID userId, int score, int rank, int ratingBefore, int ratingAfter) {
        this.historyId = historyId;
        this.userId = userId;
        this.score = score;
        this.rank = rank;
        this.ratingBefore = ratingBefore;
        this.ratingAfter = ratingAfter;
        this.ratingDelta = ratingAfter - ratingBefore;
    }

    public Long getId() { return id; }
    public Long getHistoryId() { return historyId; }
    public UUID getUserId() { return userId; }
    public int getScore() { return score; }
    public int getRank() { return rank; }
    public int getRatingBefore() { return ratingBefore; }
    public int getRatingAfter() { return ratingAfter; }
    public int getRatingDelta() { return ratingDelta; }
}
```

`MatchHistoryRepository.java`:
```java
package com.remi.stats;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchHistoryRepository extends JpaRepository<MatchHistory, Long> {
    Optional<MatchHistory> findByGameId(UUID gameId);
}
```

`MatchHistoryScoreRepository.java`:
```java
package com.remi.stats;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface MatchHistoryScoreRepository extends JpaRepository<MatchHistoryScore, Long> {
    List<MatchHistoryScore> findByUserIdOrderByIdDesc(UUID userId, PageRequest page);

    long countByUserId(UUID userId);

    @Query("SELECT COUNT(s) FROM MatchHistoryScore s WHERE s.userId = :uid AND s.rank = 1")
    long countWins(@Param("uid") UUID userId);

    @Query("SELECT COALESCE(SUM(s.score), 0) FROM MatchHistoryScore s WHERE s.userId = :uid")
    long totalPoints(@Param("uid") UUID userId);
}
```

**Add to `UserEntity.java`:** new field `rating` with getter/setter (and matching column mapping `@Column(name = "rating") private int rating = 1000;`).

- [ ] Verify: `mvn compile 2>&1 | tail -5` BUILD SUCCESS
- [ ] Commit: `git add src/main/java/com/remi/stats src/main/java/com/remi/user/persistence/UserEntity.java && git commit -m "feat(stats): MatchHistory entities + repos + rating column on UserEntity"`

### Task A3: EloCalculator (pure logic)

**File:** `src/main/java/com/remi/stats/EloCalculator.java`

```java
package com.remi.stats;

import java.util.*;

public final class EloCalculator {
    private static final int K = 32;
    private EloCalculator() {}

    /** Compute new ratings given current ratings and the final ranks (1 = winner). */
    public static Map<UUID, Integer> apply(Map<UUID, Integer> currentRatings, Map<UUID, Integer> ranks) {
        int n = currentRatings.size();
        Map<UUID, Integer> newRatings = new HashMap<>();
        for (UUID a : currentRatings.keySet()) {
            double expected = 0.0;
            double actual = 0.0;
            int rA = currentRatings.get(a);
            for (UUID b : currentRatings.keySet()) {
                if (a.equals(b)) continue;
                int rB = currentRatings.get(b);
                double e = 1.0 / (1.0 + Math.pow(10.0, (rB - rA) / 400.0));
                expected += e;
                // actual: 1 if a outranks b (lower rank number = better), 0.5 if tie
                int ra = ranks.get(a), rb = ranks.get(b);
                if (ra < rb) actual += 1.0;
                else if (ra == rb) actual += 0.5;
            }
            // Normalize: distributing K across all opponents
            double delta = (actual - expected) * K / (n - 1);
            int newRating = (int) Math.round(rA + delta);
            newRatings.put(a, Math.max(0, newRating));
        }
        return newRatings;
    }
}
```

**Test file:** `src/test/java/com/remi/stats/EloCalculatorTest.java`

```java
package com.remi.stats;

import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EloCalculatorTest {
    @Test
    void twoPlayerEqualRatingsWinnerGainsK() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        Map<UUID, Integer> r = Map.of(a, 1000, b, 1000);
        Map<UUID, Integer> ranks = Map.of(a, 1, b, 2);
        Map<UUID, Integer> result = EloCalculator.apply(r, ranks);
        assertEquals(1016, result.get(a));
        assertEquals(984, result.get(b));
    }

    @Test
    void twoPlayerHigherRatedLosesMoreOnUpset() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        Map<UUID, Integer> r = Map.of(a, 1500, b, 1000);
        Map<UUID, Integer> ranks = Map.of(a, 2, b, 1); // upset: lower-rated wins
        Map<UUID, Integer> result = EloCalculator.apply(r, ranks);
        // Lower-rated (b) should gain MORE than 16
        assertTrue(result.get(b) - 1000 > 16, "expected >16 gain for upset, got " + (result.get(b) - 1000));
    }

    @Test
    void fourPlayerWinnerGainsAcrossThreeOpponents() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        Map<UUID, Integer> r = Map.of(a, 1000, b, 1000, c, 1000, d, 1000);
        Map<UUID, Integer> ranks = Map.of(a, 1, b, 2, c, 3, d, 4);
        Map<UUID, Integer> result = EloCalculator.apply(r, ranks);
        // Winner: actual=3, expected=1.5, delta = (1.5)*32/3 ≈ 16
        assertEquals(1016, result.get(a));
        // Last place: actual=0, expected=1.5, delta = -1.5*32/3 = -16
        assertEquals(984, result.get(d));
    }

    @Test
    void ratingFloorAtZero() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        Map<UUID, Integer> r = Map.of(a, 10, b, 3000);
        Map<UUID, Integer> ranks = Map.of(a, 2, b, 1); // expected
        Map<UUID, Integer> result = EloCalculator.apply(r, ranks);
        assertTrue(result.get(a) >= 0);
    }
}
```

- [ ] Run: `mvn test -Dtest=EloCalculatorTest 2>&1 | tail -10` — 4/4 PASS
- [ ] Commit: `git add src/main/java/com/remi/stats/EloCalculator.java src/test/java/com/remi/stats/EloCalculatorTest.java && git commit -m "feat(stats): pure EloCalculator + unit tests"`

### Task A4: MatchHistoryService

**File:** `src/main/java/com/remi/stats/MatchHistoryService.java`

```java
package com.remi.stats;

import com.remi.engine.domain.GameState;
import com.remi.lobby.persistence.GamePlayerEntity;
import com.remi.lobby.persistence.GamePlayerRepository;
import com.remi.user.persistence.UserEntity;
import com.remi.user.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MatchHistoryService {
    private final MatchHistoryRepository historyRepo;
    private final MatchHistoryScoreRepository scoreRepo;
    private final UserRepository userRepo;
    private final GamePlayerRepository gamePlayers;

    public MatchHistoryService(MatchHistoryRepository historyRepo,
                               MatchHistoryScoreRepository scoreRepo,
                               UserRepository userRepo,
                               GamePlayerRepository gamePlayers) {
        this.historyRepo = historyRepo;
        this.scoreRepo = scoreRepo;
        this.userRepo = userRepo;
        this.gamePlayers = gamePlayers;
    }

    /** Idempotent: if a history row already exists for gameId, returns without doing anything. */
    @Transactional
    public void recordEnd(UUID gameId, GameState finalState, Instant startedAt) {
        if (historyRepo.findByGameId(gameId).isPresent()) return;
        List<Integer> totals = finalState.totals();
        if (totals.isEmpty()) return;
        // Resolve seat → userId via GamePlayerRepository
        List<GamePlayerEntity> seats = gamePlayers.findByGameIdOrderBySeatAsc(gameId);
        if (seats.size() != totals.size()) return; // unexpected
        // Lower score = better; rank by ascending score
        record Ranked(UUID userId, int score) {}
        List<Ranked> ranked = new ArrayList<>();
        for (int i = 0; i < seats.size(); i++) {
            UUID uid = seats.get(i).getUserId();
            if (uid == null) continue; // bot/empty seat — skip from stats
            ranked.add(new Ranked(uid, totals.get(i)));
        }
        if (ranked.size() < 2) return; // need at least 2 humans
        ranked.sort(Comparator.comparingInt(Ranked::score));
        Map<UUID, Integer> ranks = new HashMap<>();
        int prevScore = -1, prevRank = 0;
        for (int i = 0; i < ranked.size(); i++) {
            int rank = (ranked.get(i).score == prevScore) ? prevRank : i + 1;
            ranks.put(ranked.get(i).userId, rank);
            prevScore = ranked.get(i).score;
            prevRank = rank;
        }
        UUID winnerId = ranked.get(0).userId;

        // Load ratings
        Map<UUID, Integer> currentRatings = new HashMap<>();
        Map<UUID, UserEntity> userMap = new HashMap<>();
        for (UUID uid : ranks.keySet()) {
            UserEntity u = userRepo.findById(uid).orElse(null);
            if (u == null) return; // user vanished, abort
            currentRatings.put(uid, u.getRating());
            userMap.put(uid, u);
        }

        Map<UUID, Integer> newRatings = EloCalculator.apply(currentRatings, ranks);

        // Persist
        int duration = (int) java.time.Duration.between(startedAt, Instant.now()).getSeconds();
        if (duration < 0) duration = 0;
        MatchHistory history = historyRepo.save(new MatchHistory(gameId, duration, winnerId));

        for (Ranked r : ranked) {
            int before = currentRatings.get(r.userId);
            int after = newRatings.get(r.userId);
            UserEntity u = userMap.get(r.userId);
            u.setRating(after);
            userRepo.save(u);
            scoreRepo.save(new MatchHistoryScore(history.getId(), r.userId, r.score, ranks.get(r.userId), before, after));
        }
    }
}
```

**Note:** Requires `GamePlayerRepository.findByGameIdOrderBySeatAsc(UUID gameId)` — verify it exists (likely yes since Stage 7 added similar methods); if not, add the derived method.

**Note:** Also requires `UserEntity.getRating()` / `setRating(int)` — added in Task A2.

- [ ] Verify: `mvn compile 2>&1 | tail -5` BUILD SUCCESS
- [ ] Commit: `git add src/main/java/com/remi/stats/MatchHistoryService.java src/main/java/com/remi/lobby/persistence/GamePlayerRepository.java && git commit -m "feat(stats): MatchHistoryService records game end + ELO updates"`

### Task A5: Hook into GameService

**Modify:** `src/main/java/com/remi/service/GameService.java`

Inject `MatchHistoryService matchHistoryService;` via constructor.

Inside `applyAction` (and also `runBotsUntilHuman`), after the state is persisted, detect a transition from open→closed and call `matchHistoryService.recordEnd(gameId, newState, gameStartedAt)`.

**Challenge:** detecting the transition requires knowing the previous state. Simplest: ALWAYS call `recordEnd` when `newState.closed()` — the service is idempotent so duplicate calls do nothing.

`gameStartedAt` source: `GameEntity` has a `createdAt` (verify by reading entity). If yes, pass `entity.getCreatedAt()`. If no, add the column in a separate migration — but to avoid scope creep, fall back to `Instant.now()` minus a heuristic (duration_sec = 0 is acceptable for v1).

Example code at end of `applyAction` Accepted branch (after `broadcaster.broadcastState(...)`):

```java
if (ns.closed()) {
    Instant startedAt = entity.getCreatedAt() != null ? entity.getCreatedAt() : Instant.now();
    matchHistoryService.recordEnd(gameId, ns, startedAt);
}
```

Add same call at end of `runBotsUntilHuman` if `state.closed()` after the loop.

**Verify `GameEntity` has `createdAt`:** `grep "createdAt" src/main/java/com/remi/persistence/GameEntity.java`. If absent, use `Instant.now()` and accept `durationSec = 0`.

- [ ] Verify: `mvn test 2>&1 | tail -10` — all 210 existing pass + no regressions
- [ ] Commit: `git add src/main/java/com/remi/service/GameService.java && git commit -m "feat(stats): hook MatchHistoryService into GameService on game-over"`

### Task A6: StatsService + StatsController + tests

**File:** `src/main/java/com/remi/stats/StatsService.java`

```java
package com.remi.stats;

import com.remi.user.persistence.UserEntity;
import com.remi.user.persistence.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class StatsService {
    private final UserRepository users;
    private final MatchHistoryRepository historyRepo;
    private final MatchHistoryScoreRepository scoreRepo;

    public StatsService(UserRepository users, MatchHistoryRepository historyRepo, MatchHistoryScoreRepository scoreRepo) {
        this.users = users;
        this.historyRepo = historyRepo;
        this.scoreRepo = scoreRepo;
    }

    public record RecentMatch(UUID gameId, java.time.Instant finishedAt, int durationSec, int rank, int score, int ratingDelta, String winnerUsername) {}
    public record ProfileDto(UUID id, String username, int rating, long totalMatches, long wins, long losses, double winRate, long totalPoints, List<RecentMatch> recentMatches) {}
    public record LeaderboardEntry(UUID id, String username, int rating, long totalMatches, long wins) {}

    public ProfileDto profile(UUID userId) {
        UserEntity u = users.findById(userId).orElseThrow();
        long total = scoreRepo.countByUserId(userId);
        long wins = scoreRepo.countWins(userId);
        long losses = total - wins;
        double winRate = total == 0 ? 0.0 : (double) wins / total;
        long pts = scoreRepo.totalPoints(userId);

        List<MatchHistoryScore> recent = scoreRepo.findByUserIdOrderByIdDesc(userId, PageRequest.of(0, 20));
        List<RecentMatch> recents = new ArrayList<>();
        for (MatchHistoryScore s : recent) {
            MatchHistory h = historyRepo.findById(s.getHistoryId()).orElse(null);
            if (h == null) continue;
            String winnerName = users.findById(h.getWinnerId()).map(UserEntity::getUsername).orElse("?");
            recents.add(new RecentMatch(h.getGameId(), h.getFinishedAt(), h.getDurationSec(), s.getRank(), s.getScore(), s.getRatingDelta(), winnerName));
        }
        return new ProfileDto(u.getId(), u.getUsername(), u.getRating(), total, wins, losses, winRate, pts, recents);
    }

    public List<LeaderboardEntry> leaderboard(int limit) {
        return users.findTop50ByOrderByRatingDesc(PageRequest.of(0, Math.min(limit, 100))).stream()
            .map(u -> new LeaderboardEntry(
                u.getId(), u.getUsername(), u.getRating(),
                scoreRepo.countByUserId(u.getId()),
                scoreRepo.countWins(u.getId())
            )).toList();
    }
}
```

**Add to UserRepository:** `List<UserEntity> findTop50ByOrderByRatingDesc(PageRequest page);` — Spring Data derived name works with PageRequest as Pageable.

**File:** `src/main/java/com/remi/stats/StatsController.java`

```java
package com.remi.stats;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class StatsController {
    private final StatsService service;
    public StatsController(StatsService service) { this.service = service; }

    @GetMapping("/users/{id}/profile")
    public StatsService.ProfileDto profile(@PathVariable UUID id) {
        return service.profile(id);
    }

    @GetMapping("/users/me/stats")
    public StatsService.ProfileDto myStats(@AuthenticationPrincipal UUID userId) {
        return service.profile(userId);
    }

    @GetMapping("/leaderboard")
    public List<StatsService.LeaderboardEntry> leaderboard(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        return service.leaderboard(limit);
    }
}
```

**Test:** `src/test/java/com/remi/stats/StatsControllerTest.java` — 4 cases:
- profile returns 1000 rating for fresh user, 0 matches
- after a match, profile reflects rating change + 1 match + win/loss
- leaderboard orders by rating
- /api/users/me/stats returns current user's profile

Mirror FriendsControllerTest's JWT minting + helper to play a quick game (you can directly save a MatchHistory + MatchHistoryScore via repos in setup — skip actual gameplay). Reset DB state via `@Transactional` + truncation.

- [ ] Verify: `mvn test 2>&1 | tail -10` — 218+ tests passing
- [ ] Commit: `git add src/main/java/com/remi/stats/StatsService.java src/main/java/com/remi/stats/StatsController.java src/test/java/com/remi/stats/StatsControllerTest.java src/main/java/com/remi/user/persistence/UserRepository.java && git commit -m "feat(stats): StatsController + tests for profile + leaderboard"`

---

## Phase B — Frontend

### Task B1: NgRx stats feature

**Files (under `frontend/src/app/store/stats/`):**

`stats.models.ts`:
```ts
export interface RecentMatch {
  gameId: string;
  finishedAt: string;
  durationSec: number;
  rank: number;
  score: number;
  ratingDelta: number;
  winnerUsername: string;
}

export interface Profile {
  id: string;
  username: string;
  rating: number;
  totalMatches: number;
  wins: number;
  losses: number;
  winRate: number;
  totalPoints: number;
  recentMatches: RecentMatch[];
}

export interface LeaderboardEntry {
  id: string;
  username: string;
  rating: number;
  totalMatches: number;
  wins: number;
}
```

`stats.actions.ts`:
```ts
import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { Profile, LeaderboardEntry } from './stats.models';

export const StatsActions = createActionGroup({
  source: 'Stats',
  events: {
    'Load Profile': props<{ userId: string }>(),
    'Profile Loaded': props<{ profile: Profile }>(),
    'Load My Stats': emptyProps(),
    'Load Leaderboard': emptyProps(),
    'Leaderboard Loaded': props<{ entries: LeaderboardEntry[] }>(),
  },
});
```

`stats.reducer.ts`:
```ts
import { createFeature, createReducer, on } from '@ngrx/store';
import { Profile, LeaderboardEntry } from './stats.models';
import { StatsActions } from './stats.actions';

export interface StatsState {
  profiles: Record<string, Profile>;
  leaderboard: LeaderboardEntry[];
}

const initial: StatsState = { profiles: {}, leaderboard: [] };

export const statsFeature = createFeature({
  name: 'stats',
  reducer: createReducer<StatsState>(
    initial,
    on(StatsActions.profileLoaded, (s, { profile }) => ({ ...s, profiles: { ...s.profiles, [profile.id]: profile } })),
    on(StatsActions.leaderboardLoaded, (s, { entries }) => ({ ...s, leaderboard: entries })),
  ),
});
```

- [ ] Commit: `git add frontend/src/app/store/stats && git commit -m "feat(stats): NgRx models + actions + reducer"`

### Task B2: StatsApi + StatsEffects + register

**File:** `frontend/src/app/core/api/stats.api.ts`

```ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../config/api-url.tokens';
import { Profile, LeaderboardEntry } from '../../store/stats/stats.models';

@Injectable({ providedIn: 'root' })
export class StatsApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_URL);

  profile(userId: string): Promise<Profile> {
    return firstValueFrom(this.http.get<Profile>(`${this.base}/users/${userId}/profile`));
  }
  myStats(): Promise<Profile> {
    return firstValueFrom(this.http.get<Profile>(`${this.base}/users/me/stats`));
  }
  leaderboard(): Promise<LeaderboardEntry[]> {
    return firstValueFrom(this.http.get<LeaderboardEntry[]>(`${this.base}/leaderboard?limit=50`));
  }
}
```

**File:** `frontend/src/app/store/stats/stats.effects.ts`

```ts
import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { from } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';
import { StatsActions } from './stats.actions';
import { StatsApi } from '../../core/api/stats.api';

@Injectable()
export class StatsEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(StatsApi);

  loadProfile$ = createEffect(() =>
    this.actions$.pipe(
      ofType(StatsActions.loadProfile),
      switchMap(({ userId }) => from(this.api.profile(userId)).pipe(
        map(profile => StatsActions.profileLoaded({ profile })),
      )),
    ));

  loadMyStats$ = createEffect(() =>
    this.actions$.pipe(
      ofType(StatsActions.loadMyStats),
      switchMap(() => from(this.api.myStats()).pipe(
        map(profile => StatsActions.profileLoaded({ profile })),
      )),
    ));

  loadLeaderboard$ = createEffect(() =>
    this.actions$.pipe(
      ofType(StatsActions.loadLeaderboard),
      switchMap(() => from(this.api.leaderboard()).pipe(
        map(entries => StatsActions.leaderboardLoaded({ entries })),
      )),
    ));
}
```

- [ ] Register `statsFeature` + `StatsEffects` in `app.config.ts`.
- [ ] Verify build exit 0.
- [ ] Commit: `git add frontend/src/app/core/api/stats.api.ts frontend/src/app/store/stats/stats.effects.ts frontend/src/app/app.config.ts && git commit -m "feat(stats): StatsApi + effects + register feature"`

### Task B3: ProfilePage + LeaderboardPage

**File:** `frontend/src/app/features/stats/profile.page.ts`

```ts
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { Store } from '@ngrx/store';
import { IonContent, IonHeader, IonToolbar, IonTitle, IonCard, IonCardHeader, IonCardTitle, IonCardContent, IonList, IonItem, IonLabel, IonBackButton, IonButtons } from '@ionic/angular/standalone';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { StatsActions } from '../../store/stats/stats.actions';
import { statsFeature } from '../../store/stats/stats.reducer';
import { Profile } from '../../store/stats/stats.models';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, IonContent, IonHeader, IonToolbar, IonTitle, IonCard, IonCardHeader, IonCardTitle, IonCardContent, IonList, IonItem, IonLabel, IonBackButton, IonButtons],
  template: `
    <ion-header><ion-toolbar><ion-buttons slot="start"><ion-back-button></ion-back-button></ion-buttons><ion-title>Profil</ion-title></ion-toolbar></ion-header>
    <ion-content>
      <ng-container *ngIf="(profile$ | async) as p">
        <ion-card>
          <ion-card-header><ion-card-title>{{ p.username }}</ion-card-title></ion-card-header>
          <ion-card-content>
            <p><strong>Rating:</strong> {{ p.rating }}</p>
            <p><strong>Meciuri:</strong> {{ p.totalMatches }} ({{ p.wins }}W / {{ p.losses }}L)</p>
            <p><strong>Rată câștig:</strong> {{ (p.winRate * 100).toFixed(1) }}%</p>
            <p><strong>Puncte totale:</strong> {{ p.totalPoints }}</p>
          </ion-card-content>
        </ion-card>
        <ion-list>
          <ion-item *ngFor="let m of p.recentMatches">
            <ion-label>
              <h2>vs {{ m.winnerUsername }} ({{ m.rank === 1 ? 'win' : 'loss' }})</h2>
              <p>Scor: {{ m.score }} | ΔRating: {{ m.ratingDelta > 0 ? '+' : '' }}{{ m.ratingDelta }} | {{ m.finishedAt | date:'short' }}</p>
            </ion-label>
          </ion-item>
        </ion-list>
      </ng-container>
    </ion-content>
  `,
})
export class ProfilePage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly store = inject(Store);
  profile$!: Observable<Profile | null>;
  userId = '';

  ngOnInit(): void {
    this.userId = this.route.snapshot.paramMap.get('id')!;
    this.store.dispatch(StatsActions.loadProfile({ userId: this.userId }));
    this.profile$ = this.store.select(statsFeature.selectProfiles).pipe(map(ps => ps[this.userId] ?? null));
  }
}
```

**File:** `frontend/src/app/features/stats/leaderboard.page.ts`

```ts
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { IonContent, IonHeader, IonToolbar, IonTitle, IonList, IonItem, IonLabel, IonBackButton, IonButtons, IonBadge } from '@ionic/angular/standalone';
import { StatsActions } from '../../store/stats/stats.actions';
import { statsFeature } from '../../store/stats/stats.reducer';

@Component({
  selector: 'app-leaderboard',
  standalone: true,
  imports: [CommonModule, RouterLink, IonContent, IonHeader, IonToolbar, IonTitle, IonList, IonItem, IonLabel, IonBackButton, IonButtons, IonBadge],
  template: `
    <ion-header><ion-toolbar><ion-buttons slot="start"><ion-back-button></ion-back-button></ion-buttons><ion-title>Clasament</ion-title></ion-toolbar></ion-header>
    <ion-content>
      <ion-list>
        <ion-item *ngFor="let e of (entries$ | async); let i = index" [routerLink]="['/profile', e.id]">
          <ion-label>
            <h2>#{{ i + 1 }} {{ e.username }}</h2>
            <p>{{ e.totalMatches }} meciuri, {{ e.wins }} câștigate</p>
          </ion-label>
          <ion-badge slot="end">{{ e.rating }}</ion-badge>
        </ion-item>
      </ion-list>
    </ion-content>
  `,
})
export class LeaderboardPage implements OnInit {
  private readonly store = inject(Store);
  readonly entries$ = this.store.select(statsFeature.selectLeaderboard);

  ngOnInit(): void { this.store.dispatch(StatsActions.loadLeaderboard()); }
}
```

- [ ] Add routes in `app.routes.ts`:
```ts
{ path: 'profile/:id', loadComponent: () => import('./features/stats/profile.page').then(m => m.ProfilePage) },
{ path: 'leaderboard', loadComponent: () => import('./features/stats/leaderboard.page').then(m => m.LeaderboardPage) },
```
- [ ] Add buttons in LobbyHomePage template: "Statistici" → `/profile/<myUserId>` (use auth selector for current user ID), "Clasament" → `/leaderboard`.
- [ ] Make friend list items in FriendsListItem (or wherever friends are rendered) navigate to `/profile/<friend.id>` on tap.
- [ ] Verify build exit 0 + Karma 147 still PASS.
- [ ] Commit: `git add frontend/src/app/features/stats frontend/src/app/app.routes.ts frontend/src/app/features/lobby frontend/src/app/features/friends && git commit -m "feat(stats): Profile + Leaderboard pages with navigation"`

---

## Phase C — Docs

### Task C1: README + final note

- [ ] Append a Stats section to `frontend/README.md`.
- [ ] Commit: `git commit -m "docs(stage8): stats overview"`
