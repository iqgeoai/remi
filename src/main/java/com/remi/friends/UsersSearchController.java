package com.remi.friends;

import com.remi.user.persistence.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UsersSearchController {
    private final UserRepository users;
    private final UserBlockRepository blocks;
    private final FriendshipRepository friendships;

    public UsersSearchController(UserRepository users, UserBlockRepository blocks, FriendshipRepository friendships) {
        this.users = users;
        this.blocks = blocks;
        this.friendships = friendships;
    }

    @GetMapping("/search")
    public List<FriendsService.SearchHit> search(@AuthenticationPrincipal UUID actorId, @RequestParam("q") String q) {
        if (q == null || q.length() < 2) return List.of();
        String prefix = q.toLowerCase();
        return users.findTop20ByUsernameNormalizedStartingWith(prefix).stream()
            .filter(u -> !u.getId().equals(actorId))
            .filter(u -> !blocks.existsByBlockerIdAndBlockedId(actorId, u.getId()))
            .filter(u -> !blocks.existsByBlockerIdAndBlockedId(u.getId(), actorId))
            .filter(u -> friendships.findBetween(actorId, u.getId())
                .map(f -> f.getStatus() == FriendshipStatus.REJECTED || f.getStatus() == FriendshipStatus.CANCELLED)
                .orElse(true))
            .map(u -> new FriendsService.SearchHit(u.getId(), u.getUsername()))
            .toList();
    }
}
