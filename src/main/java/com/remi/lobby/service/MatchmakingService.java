package com.remi.lobby.service;

import com.remi.lobby.domain.LobbyGame;
import com.remi.lobby.domain.MatchConfig;
import java.util.Optional;
import java.util.UUID;

public interface MatchmakingService {
  Optional<LobbyGame> enqueue(UUID userId, MatchConfig config);
  void cancel(UUID userId);
  int queueDepth(MatchConfig config);
}
