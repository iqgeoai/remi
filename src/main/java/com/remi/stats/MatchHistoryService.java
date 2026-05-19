package com.remi.stats;

import com.remi.engine.domain.GameState;
import com.remi.lobby.persistence.GamePlayerEntity;
import com.remi.lobby.persistence.GamePlayerRepository;
import com.remi.user.persistence.UserEntity;
import com.remi.user.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        // Resolve seat -> userId via GamePlayerRepository (ordered by player_idx ascending)
        List<GamePlayerEntity> seats = gamePlayers.findByGameIdOrderByPlayerIdxAsc(gameId);
        if (seats.size() != totals.size()) return; // unexpected (e.g. solo/bot game without seats)
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
            int rank = (ranked.get(i).score() == prevScore) ? prevRank : i + 1;
            ranks.put(ranked.get(i).userId(), rank);
            prevScore = ranked.get(i).score();
            prevRank = rank;
        }
        UUID winnerId = ranked.get(0).userId();

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
            int before = currentRatings.get(r.userId());
            int after = newRatings.get(r.userId());
            UserEntity u = userMap.get(r.userId());
            u.setRating(after);
            userRepo.save(u);
            scoreRepo.save(new MatchHistoryScore(history.getId(), r.userId(), r.score(), ranks.get(r.userId()), before, after));
        }
    }
}
