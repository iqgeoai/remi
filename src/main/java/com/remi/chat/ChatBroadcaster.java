package com.remi.chat;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;

@Component
public class ChatBroadcaster {
    private final SimpMessagingTemplate ws;

    public ChatBroadcaster(SimpMessagingTemplate ws) {
        this.ws = ws;
    }

    public void broadcastMatch(UUID matchId, ChatMessage msg, String senderUsername) {
        ws.convertAndSend("/topic/chat/match/" + matchId, payload(msg, senderUsername));
    }

    public void broadcastDm(UUID senderId, UUID receiverId, ChatMessage msg, String senderUsername) {
        Map<String, Object> p = payload(msg, senderUsername);
        ws.convertAndSendToUser(senderId.toString(), "/queue/dm/" + receiverId, p);
        ws.convertAndSendToUser(receiverId.toString(), "/queue/dm/" + senderId, p);
    }

    private Map<String, Object> payload(ChatMessage msg, String senderUsername) {
        return Map.of(
            "id", msg.getId(),
            "senderId", msg.getSenderId().toString(),
            "senderUsername", senderUsername,
            "body", msg.getBody(),
            "createdAt", msg.getCreatedAt().toString()
        );
    }
}
