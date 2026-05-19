package com.remi.stats;

import com.remi.user.persistence.UserEntity;
import com.remi.user.persistence.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
