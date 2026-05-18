package com.remi.chat;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService service;

    public ChatController(ChatService service) { this.service = service; }

    public record SendReq(String body) {}

    @GetMapping("/match/{matchId}")
    public List<ChatService.MessageDto> matchHistory(@AuthenticationPrincipal UUID actor, @PathVariable UUID matchId,
                                                     @RequestParam(value = "limit", defaultValue = "200") int limit) {
        return service.matchHistory(actor, matchId, limit);
    }

    @PostMapping("/match/{matchId}")
    public ResponseEntity<Map<String, Object>> sendMatch(@AuthenticationPrincipal UUID actor, @PathVariable UUID matchId, @RequestBody SendReq req) {
        try {
            Long id = service.sendMatch(actor, matchId, req.body());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
        } catch (ChatService.RateLimitException e) {
            return ResponseEntity.status(429).build();
        }
    }

    @GetMapping("/dm/conversations")
    public List<ChatService.ConversationDto> conversations(@AuthenticationPrincipal UUID actor) {
        return service.listConversations(actor);
    }

    @GetMapping("/dm/{otherUserId}")
    public List<ChatService.MessageDto> dmHistory(@AuthenticationPrincipal UUID actor, @PathVariable UUID otherUserId,
                                                  @RequestParam(value = "limit", defaultValue = "200") int limit) {
        return service.dmHistory(actor, otherUserId, limit);
    }

    @PostMapping("/dm/{otherUserId}")
    public ResponseEntity<Map<String, Object>> sendDm(@AuthenticationPrincipal UUID actor, @PathVariable UUID otherUserId, @RequestBody SendReq req) {
        try {
            Long id = service.sendDm(actor, otherUserId, req.body());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
        } catch (ChatService.RateLimitException e) {
            return ResponseEntity.status(429).build();
        }
    }
}
