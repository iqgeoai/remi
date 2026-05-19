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
