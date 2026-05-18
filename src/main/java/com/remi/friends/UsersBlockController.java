package com.remi.friends;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UsersBlockController {
    private final FriendsService service;
    public UsersBlockController(FriendsService service) { this.service = service; }

    @PostMapping("/{userId}/block")
    public ResponseEntity<Void> block(@AuthenticationPrincipal UUID actorId, @PathVariable UUID userId) {
        service.block(actorId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}/block")
    public ResponseEntity<Void> unblock(@AuthenticationPrincipal UUID actorId, @PathVariable UUID userId) {
        service.unblock(actorId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/blocked")
    public List<FriendsService.SearchHit> blocked(@AuthenticationPrincipal UUID actorId) {
        return service.blockedList(actorId);
    }
}
