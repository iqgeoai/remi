package com.remi.friends;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/friends")
public class FriendsController {
    private final FriendsService service;

    public FriendsController(FriendsService service) {
        this.service = service;
    }

    public record SendRequestReq(UUID addresseeId) {}

    @GetMapping
    public List<FriendsService.FriendDto> list(@AuthenticationPrincipal UUID userId) {
        return service.listFriends(userId);
    }

    @GetMapping("/requests")
    public Map<String, Object> requests(@AuthenticationPrincipal UUID userId) {
        return Map.of(
            "incoming", service.incoming(userId),
            "outgoing", service.outgoing(userId)
        );
    }

    @PostMapping("/requests")
    public ResponseEntity<Map<String, Object>> send(@AuthenticationPrincipal UUID userId, @RequestBody SendRequestReq req) {
        Long id = service.sendRequest(userId, req.addresseeId());
        return ResponseEntity.status(201).body(Map.of("id", id));
    }

    @PostMapping("/requests/{id}/accept")
    public ResponseEntity<Void> accept(@AuthenticationPrincipal UUID userId, @PathVariable Long id) {
        service.acceptRequest(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/requests/{id}/reject")
    public ResponseEntity<Void> reject(@AuthenticationPrincipal UUID userId, @PathVariable Long id) {
        service.rejectRequest(userId, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/requests/{id}")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal UUID userId, @PathVariable Long id) {
        service.cancelRequest(userId, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{friendId}")
    public ResponseEntity<Void> unfriend(@AuthenticationPrincipal UUID userId, @PathVariable UUID friendId) {
        service.unfriend(userId, friendId);
        return ResponseEntity.noContent().build();
    }
}
