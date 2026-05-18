package com.remi.friends;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class FriendsBroadcaster {
    private final SimpMessagingTemplate ws;

    public FriendsBroadcaster(SimpMessagingTemplate ws) {
        this.ws = ws;
    }

    public void notifyIncomingRequest(UUID addresseeId, Long requestId, UUID fromUserId, String fromUsername) {
        ws.convertAndSendToUser(addresseeId.toString(), "/queue/friend-requests",
            Map.of("type", "incoming", "requestId", requestId, "fromUserId", fromUserId.toString(), "fromUsername", fromUsername));
    }

    public void notifyAccepted(UUID requesterId, Long requestId) {
        ws.convertAndSendToUser(requesterId.toString(), "/queue/friend-requests",
            Map.of("type", "accepted", "requestId", requestId));
    }

    public void notifyPresence(UUID toUserId, UUID userId, boolean online, Instant since) {
        Map<String, Object> payload = since != null
            ? Map.of("userId", userId.toString(), "online", online, "since", since.toString())
            : Map.of("userId", userId.toString(), "online", online);
        ws.convertAndSendToUser(toUserId.toString(), "/queue/presence", payload);
    }

    public void notifyFriendInvite(UUID toUserId, UUID fromUserId, String fromUsername, String code, UUID matchId) {
        ws.convertAndSendToUser(toUserId.toString(), "/queue/invites",
            Map.of("type", "friend-invite", "fromUserId", fromUserId.toString(),
                   "fromUsername", fromUsername, "code", code, "matchId", matchId.toString()));
    }
}
