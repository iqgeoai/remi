package com.remi.lobby.api;

import com.remi.lobby.domain.MatchConfig;
import com.remi.lobby.service.MatchmakingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/matchmaking")
public class MatchmakingController {
  private final MatchmakingService matchService;

  public MatchmakingController(MatchmakingService matchService) { this.matchService = matchService; }

  @PostMapping("/quick")
  public QuickMatchResponse quick(@Valid @RequestBody QuickMatchRequest req, @AuthenticationPrincipal UUID userId) {
    var match = matchService.enqueue(userId, new MatchConfig(req.numPlayers(), req.mode(), req.difficulty()));
    return new QuickMatchResponse(match.isPresent(), match.orElse(null));
  }

  @PostMapping("/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(@AuthenticationPrincipal UUID userId) { matchService.cancel(userId); }
}
