package com.remi.ws.broadcast;

import com.remi.api.GameView;
import com.remi.engine.domain.DomainEvent;
import com.remi.engine.domain.GameState;
import com.remi.lobby.persistence.GamePlayerEntity;
import com.remi.lobby.persistence.GamePlayerRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class StompGameBroadcaster implements GameBroadcaster {
  private final SimpMessagingTemplate stomp;
  private final GamePlayerRepository players;

  public StompGameBroadcaster(SimpMessagingTemplate stomp, GamePlayerRepository players) {
    this.stomp = stomp;
    this.players = players;
  }

  @Override
  public void broadcastState(UUID gameId, GameState newState, List<DomainEvent> events) {
    String destination = "/queue/games/" + gameId;
    for (GamePlayerEntity p : players.findByGameIdOrderByPlayerIdxAsc(gameId)) {
      GameView view = GameView.of(newState, p.getPlayerIdx());
      stomp.convertAndSendToUser(p.getUserId().toString(), destination,
          Map.of("view", view, "events", events));
    }
  }
}
