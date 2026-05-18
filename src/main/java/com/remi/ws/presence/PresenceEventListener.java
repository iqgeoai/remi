package com.remi.ws.presence;

import com.remi.friends.FriendshipRepository;
import com.remi.friends.PresenceRegistry;
import com.remi.friends.FriendsBroadcaster;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.UUID;

@Component
public class PresenceEventListener {
  private final PresenceRegistry presence;
  private final FriendshipRepository friendships;
  private final FriendsBroadcaster broadcaster;

  public PresenceEventListener(
      PresenceRegistry presence,
      FriendshipRepository friendships,
      FriendsBroadcaster broadcaster) {
    this.presence = presence;
    this.friendships = friendships;
    this.broadcaster = broadcaster;
  }

  @EventListener
  public void onConnect(SessionConnectedEvent e) {
    StompHeaderAccessor acc = StompHeaderAccessor.wrap(e.getMessage());
    Principal p = acc.getUser();
    if (p == null) return;
    UUID userId = UUID.fromString(p.getName());
    presence.markOnline(userId);
    notifyFriends(userId, true);
  }

  @EventListener
  public void onDisconnect(SessionDisconnectEvent e) {
    StompHeaderAccessor acc = StompHeaderAccessor.wrap(e.getMessage());
    Principal p = acc.getUser();
    if (p == null) return;
    UUID userId = UUID.fromString(p.getName());
    presence.markOffline(userId);
    notifyFriends(userId, false);
  }

  private void notifyFriends(UUID userId, boolean online) {
    friendships.findAccepted(userId).forEach(f -> {
      UUID other = f.getRequesterId().equals(userId) ? f.getAddresseeId() : f.getRequesterId();
      broadcaster.notifyPresence(other, userId, online, online ? java.time.Instant.now() : null);
    });
  }
}
