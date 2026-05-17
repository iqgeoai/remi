package com.remi.ws.config;

import com.remi.lobby.persistence.GamePlayerRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StompSubscriptionInterceptor implements ChannelInterceptor {
  // Matches /user/queue/games/{uuid}
  private static final Pattern GAME_TOPIC = Pattern.compile("^/user/queue/games/([0-9a-fA-F-]+)$");

  private final GamePlayerRepository players;

  public StompSubscriptionInterceptor(GamePlayerRepository players) { this.players = players; }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
    if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
      String destination = accessor.getDestination();
      if (destination == null) return message;
      Matcher m = GAME_TOPIC.matcher(destination);
      if (!m.matches()) return message;   // not a game topic; let other subscriptions through
      UUID gameId = UUID.fromString(m.group(1));
      StompPrincipal principal = (StompPrincipal) accessor.getUser();
      if (principal == null) throw new AccessDeniedException("Not authenticated");
      if (players.findSeat(gameId, principal.userId()).isEmpty()) {
        throw new AccessDeniedException("Not seated at game " + gameId);
      }
    }
    return message;
  }
}
