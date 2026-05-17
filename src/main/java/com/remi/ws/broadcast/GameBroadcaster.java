package com.remi.ws.broadcast;

import com.remi.engine.domain.DomainEvent;
import com.remi.engine.domain.GameState;
import java.util.List;
import java.util.UUID;

public interface GameBroadcaster {
  void broadcastState(UUID gameId, GameState newState, List<DomainEvent> events);
}
