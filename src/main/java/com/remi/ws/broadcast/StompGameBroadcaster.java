package com.remi.ws.broadcast;

import com.remi.api.GameView;
import com.remi.engine.domain.DomainEvent;
import com.remi.engine.domain.GameState;
import com.remi.lobby.persistence.GamePlayerEntity;
import com.remi.lobby.persistence.GamePlayerRepository;
import com.remi.push.PushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class StompGameBroadcaster implements GameBroadcaster {
  private static final Logger log = LoggerFactory.getLogger(StompGameBroadcaster.class);
  private final SimpMessagingTemplate stomp;
  private final GamePlayerRepository players;
  private final PushService push;

  public StompGameBroadcaster(SimpMessagingTemplate stomp, GamePlayerRepository players, PushService push) {
    this.stomp = stomp;
    this.players = players;
    this.push = push;
  }

  @Override
  public void broadcastState(UUID gameId, GameState newState, List<DomainEvent> events) {
    String destination = "/queue/games/" + gameId;
    int activeIdx = newState.current();
    for (GamePlayerEntity p : players.findByGameIdOrderByPlayerIdxAsc(gameId)) {
      GameView view = GameView.of(newState, p.getPlayerIdx());
      stomp.convertAndSendToUser(p.getUserId().toString(), destination,
          Map.of("view", view, "events", events));
      if (p.getPlayerIdx() == activeIdx) {
        try {
          push.notify(p.getUserId(), "E rândul tău", "Joacă acum în Remi",
              Map.of("type", "turn_ready", "matchId", gameId.toString()));
        } catch (Exception e) {
          log.warn("Push notify (turn_ready) failed for user {}: {}", p.getUserId(), e.getMessage());
        }
      }
    }
  }
}
