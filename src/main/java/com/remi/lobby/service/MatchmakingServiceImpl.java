package com.remi.lobby.service;

import com.remi.lobby.domain.LobbyGame;
import com.remi.lobby.domain.MatchConfig;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MatchmakingServiceImpl implements MatchmakingService {
  private final LobbyService lobby;
  private final Map<MatchConfig, Deque<UUID>> queues = new ConcurrentHashMap<>();
  private final Set<UUID> queuedUsers = ConcurrentHashMap.newKeySet();

  public MatchmakingServiceImpl(LobbyService lobby) { this.lobby = lobby; }

  @Override
  public Optional<LobbyGame> enqueue(UUID userId, MatchConfig config) {
    Deque<UUID> q = queues.computeIfAbsent(config, k -> new ArrayDeque<>());
    synchronized (q) {
      if (!queuedUsers.add(userId)) throw new MatchmakingAlreadyQueuedException();
      q.add(userId);
      if (q.size() >= config.numPlayers()) {
        List<UUID> picked = new ArrayList<>(config.numPlayers());
        for (int i = 0; i < config.numPlayers(); i++) picked.add(q.poll());
        picked.forEach(queuedUsers::remove);
        LobbyGame game = lobby.createPublicForUsers(picked, config.numPlayers(), config.mode(), config.difficulty());
        return Optional.of(game);
      }
      return Optional.empty();
    }
  }

  @Override
  public void cancel(UUID userId) {
    queuedUsers.remove(userId);
    for (Deque<UUID> q : queues.values()) {
      synchronized (q) { q.remove(userId); }
    }
  }

  @Override
  public int queueDepth(MatchConfig config) {
    Deque<UUID> q = queues.get(config);
    if (q == null) return 0;
    synchronized (q) { return q.size(); }
  }

  /** Test-only: clears all in-memory queue state. */
  void clearAllForTest() {
    queues.clear();
    queuedUsers.clear();
  }
}
