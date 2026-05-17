package com.remi.lobby.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class GameTimerServiceImpl implements GameTimerService {
  private final Duration hardTimeout;
  private final Map<UUID, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
  private TaskScheduler scheduler;

  public GameTimerServiceImpl(@Value("${game-timer.hard-timeout}") Duration hardTimeout) {
    this.hardTimeout = hardTimeout;
  }

  @PostConstruct
  void init() {
    ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
    s.setPoolSize(4);
    s.setThreadNamePrefix("game-timer-");
    s.initialize();
    this.scheduler = s;
  }

  @PreDestroy
  void shutdown() {
    if (scheduler instanceof ThreadPoolTaskScheduler tps) tps.shutdown();
  }

  @Override
  public void scheduleHardTimeout(UUID gameId, int playerIdx, Runnable onTimeout) {
    cancel(gameId);
    ScheduledFuture<?> f = scheduler.schedule(onTimeout, Instant.now().plus(hardTimeout));
    tasks.put(gameId, f);
  }

  @Override
  public void cancel(UUID gameId) {
    ScheduledFuture<?> existing = tasks.remove(gameId);
    if (existing != null) existing.cancel(false);
  }
}
