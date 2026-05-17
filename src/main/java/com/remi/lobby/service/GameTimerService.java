package com.remi.lobby.service;

import java.util.UUID;

public interface GameTimerService {
  void scheduleHardTimeout(UUID gameId, int playerIdx, Runnable onTimeout);
  void cancel(UUID gameId);
}
