# Stage 6 — Friends + Presence Implementation Plan

> **For agentic workers:** Subagent-driven execution.

**Spec:** `docs/superpowers/specs/2026-05-18-stage6-friends-presence-design.md`

**Architecture:** Friendship + block tables, in-memory PresenceRegistry, REST endpoints for CRUD, WS topics for live updates, NgRx frontend feature.

---

## Phase A — Backend persistence

### Task A1: V5 migration

**File:** `src/main/resources/db/migration/V5__friends_blocks.sql`

```sql
CREATE TYPE friendship_status AS ENUM ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED');

CREATE TABLE friendships (
    id           BIGSERIAL PRIMARY KEY,
    requester_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    addressee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status       friendship_status NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at  TIMESTAMPTZ,
    CONSTRAINT friendships_distinct CHECK (requester_id <> addressee_id),
    CONSTRAINT friendships_unique_pair UNIQUE (requester_id, addressee_id)
);
CREATE INDEX friendships_requester_idx ON friendships(requester_id);
CREATE INDEX friendships_addressee_idx ON friendships(addressee_id);
CREATE INDEX friendships_status_accepted_idx ON friendships(status) WHERE status = 'ACCEPTED';

CREATE TABLE user_blocks (
    id         BIGSERIAL PRIMARY KEY,
    blocker_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT user_blocks_distinct CHECK (blocker_id <> blocked_id),
    CONSTRAINT user_blocks_unique UNIQUE (blocker_id, blocked_id)
);
CREATE INDEX user_blocks_blocker_idx ON user_blocks(blocker_id);
```

- [ ] Verify: `mvn test -Dtest='*Migration*' 2>&1 | tail -5` OR `mvn test 2>&1 | tail -3` BUILD SUCCESS
- [ ] Commit: `git add src/main/resources/db/migration/V5__friends_blocks.sql && git commit -m "feat(friends): V5 migration friendships + user_blocks"`

### Task A2: JPA entities + repos

**Files (under `src/main/java/com/remi/friends/`):**

`Friendship.java`:
```java
package com.remi.friends;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "friendships")
public class Friendship {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "addressee_id", nullable = false)
    private UUID addresseeId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "friendship_status")
    private FriendshipStatus status = FriendshipStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    protected Friendship() {}

    public Friendship(UUID requesterId, UUID addresseeId) {
        this.requesterId = requesterId;
        this.addresseeId = addresseeId;
    }

    public Long getId() { return id; }
    public UUID getRequesterId() { return requesterId; }
    public UUID getAddresseeId() { return addresseeId; }
    public FriendshipStatus getStatus() { return status; }
    public void setStatus(FriendshipStatus s) { this.status = s; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant a) { this.acceptedAt = a; }
}
```

`FriendshipStatus.java`:
```java
package com.remi.friends;
public enum FriendshipStatus { PENDING, ACCEPTED, REJECTED, CANCELLED }
```

`FriendshipRepository.java`:
```java
package com.remi.friends;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    @Query("SELECT f FROM Friendship f WHERE (f.requesterId = :a AND f.addresseeId = :b) OR (f.requesterId = :b AND f.addresseeId = :a)")
    Optional<Friendship> findBetween(@Param("a") UUID a, @Param("b") UUID b);

    @Query("SELECT f FROM Friendship f WHERE f.status = com.remi.friends.FriendshipStatus.ACCEPTED AND (f.requesterId = :userId OR f.addresseeId = :userId)")
    List<Friendship> findAccepted(@Param("userId") UUID userId);

    List<Friendship> findByAddresseeIdAndStatus(UUID addresseeId, FriendshipStatus status);
    List<Friendship> findByRequesterIdAndStatus(UUID requesterId, FriendshipStatus status);
}
```

`UserBlock.java`:
```java
package com.remi.friends;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_blocks")
public class UserBlock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_id", nullable = false)
    private UUID blockerId;

    @Column(name = "blocked_id", nullable = false)
    private UUID blockedId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected UserBlock() {}

    public UserBlock(UUID blockerId, UUID blockedId) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
    }

    public Long getId() { return id; }
    public UUID getBlockerId() { return blockerId; }
    public UUID getBlockedId() { return blockedId; }
    public Instant getCreatedAt() { return createdAt; }
}
```

`UserBlockRepository.java`:
```java
package com.remi.friends;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {
    Optional<UserBlock> findByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
    List<UserBlock> findByBlockerId(UUID blockerId);
    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
}
```

- [ ] Verify: `mvn compile 2>&1 | tail -5` BUILD SUCCESS
- [ ] Commit: `git add src/main/java/com/remi/friends && git commit -m "feat(friends): JPA entities + repos for friendships and user_blocks"`

### Task A3: PresenceRegistry (in-memory)

**File:** `src/main/java/com/remi/friends/PresenceRegistry.java`

```java
package com.remi.friends;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PresenceRegistry {
    private final Map<UUID, Instant> online = new ConcurrentHashMap<>();

    public void markOnline(UUID userId) {
        online.put(userId, Instant.now());
    }

    public void markOffline(UUID userId) {
        online.remove(userId);
    }

    public boolean isOnline(UUID userId) {
        return online.containsKey(userId);
    }

    public Optional<Instant> since(UUID userId) {
        return Optional.ofNullable(online.get(userId));
    }

    public Map<UUID, Instant> snapshot() {
        return Map.copyOf(online);
    }
}
```

- [ ] Verify: `mvn compile 2>&1 | tail -5` BUILD SUCCESS
- [ ] Commit: `git add src/main/java/com/remi/friends/PresenceRegistry.java && git commit -m "feat(friends): in-memory PresenceRegistry"`

---

## Phase B — Backend service + controllers

### Task B1: FriendsService

**File:** `src/main/java/com/remi/friends/FriendsService.java`

```java
package com.remi.friends;

import com.remi.user.persistence.UserRepository;
import com.remi.user.persistence.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class FriendsService {
    private final FriendshipRepository friendships;
    private final UserBlockRepository blocks;
    private final UserRepository users;
    private final PresenceRegistry presence;
    private final FriendsBroadcaster broadcaster;

    public FriendsService(FriendshipRepository friendships, UserBlockRepository blocks,
                          UserRepository users, PresenceRegistry presence, FriendsBroadcaster broadcaster) {
        this.friendships = friendships;
        this.blocks = blocks;
        this.users = users;
        this.presence = presence;
        this.broadcaster = broadcaster;
    }

    public record FriendDto(UUID id, String username, boolean online, Instant since) {}
    public record RequestDto(Long id, UUID userId, String username, Instant createdAt) {}
    public record SearchHit(UUID id, String username) {}

    public List<FriendDto> listFriends(UUID userId) {
        return friendships.findAccepted(userId).stream()
            .map(f -> f.getRequesterId().equals(userId) ? f.getAddresseeId() : f.getRequesterId())
            .map(uid -> users.findById(uid).map(u -> new FriendDto(
                u.getId(), u.getUsername(),
                presence.isOnline(u.getId()),
                presence.since(u.getId()).orElse(null)
            )))
            .flatMap(Optional::stream)
            .collect(Collectors.toList());
    }

    public List<RequestDto> incoming(UUID userId) {
        return friendships.findByAddresseeIdAndStatus(userId, FriendshipStatus.PENDING).stream()
            .map(f -> users.findById(f.getRequesterId()).map(u -> new RequestDto(f.getId(), u.getId(), u.getUsername(), f.getCreatedAt())))
            .flatMap(Optional::stream)
            .collect(Collectors.toList());
    }

    public List<RequestDto> outgoing(UUID userId) {
        return friendships.findByRequesterIdAndStatus(userId, FriendshipStatus.PENDING).stream()
            .map(f -> users.findById(f.getAddresseeId()).map(u -> new RequestDto(f.getId(), u.getId(), u.getUsername(), f.getCreatedAt())))
            .flatMap(Optional::stream)
            .collect(Collectors.toList());
    }

    public Long sendRequest(UUID requesterId, UUID addresseeId) {
        if (requesterId.equals(addresseeId)) throw new IllegalArgumentException("Cannot friend yourself");
        if (blocks.existsByBlockerIdAndBlockedId(addresseeId, requesterId)) throw new IllegalStateException("Blocked");
        if (blocks.existsByBlockerIdAndBlockedId(requesterId, addresseeId)) throw new IllegalStateException("You blocked them");
        Optional<Friendship> existing = friendships.findBetween(requesterId, addresseeId);
        if (existing.isPresent()) {
            Friendship f = existing.get();
            if (f.getStatus() == FriendshipStatus.ACCEPTED || f.getStatus() == FriendshipStatus.PENDING) {
                throw new IllegalStateException("Already exists");
            }
            // REJECTED or CANCELLED — allow new request
            f.setStatus(FriendshipStatus.PENDING);
            friendships.save(f);
            broadcaster.notifyIncomingRequest(addresseeId, f.getId(), requesterId, users.findById(requesterId).map(UserEntity::getUsername).orElse(""));
            return f.getId();
        }
        Friendship f = friendships.save(new Friendship(requesterId, addresseeId));
        broadcaster.notifyIncomingRequest(addresseeId, f.getId(), requesterId, users.findById(requesterId).map(UserEntity::getUsername).orElse(""));
        return f.getId();
    }

    public void acceptRequest(UUID actorId, Long requestId) {
        Friendship f = friendships.findById(requestId).orElseThrow();
        if (!f.getAddresseeId().equals(actorId)) throw new SecurityException("Not the addressee");
        if (f.getStatus() != FriendshipStatus.PENDING) throw new IllegalStateException("Not pending");
        f.setStatus(FriendshipStatus.ACCEPTED);
        f.setAcceptedAt(Instant.now());
        broadcaster.notifyAccepted(f.getRequesterId(), requestId);
    }

    public void rejectRequest(UUID actorId, Long requestId) {
        Friendship f = friendships.findById(requestId).orElseThrow();
        if (!f.getAddresseeId().equals(actorId)) throw new SecurityException("Not the addressee");
        if (f.getStatus() != FriendshipStatus.PENDING) throw new IllegalStateException("Not pending");
        f.setStatus(FriendshipStatus.REJECTED);
    }

    public void cancelRequest(UUID actorId, Long requestId) {
        Friendship f = friendships.findById(requestId).orElseThrow();
        if (!f.getRequesterId().equals(actorId)) throw new SecurityException("Not the requester");
        if (f.getStatus() != FriendshipStatus.PENDING) throw new IllegalStateException("Not pending");
        f.setStatus(FriendshipStatus.CANCELLED);
    }

    public void unfriend(UUID actorId, UUID otherId) {
        Friendship f = friendships.findBetween(actorId, otherId).orElseThrow();
        if (f.getStatus() != FriendshipStatus.ACCEPTED) throw new IllegalStateException("Not friends");
        friendships.delete(f);
    }

    public void block(UUID actorId, UUID toBlock) {
        if (actorId.equals(toBlock)) throw new IllegalArgumentException("Cannot block self");
        if (blocks.findByBlockerIdAndBlockedId(actorId, toBlock).isPresent()) return;
        blocks.save(new UserBlock(actorId, toBlock));
        // Delete any existing friendship in either direction
        friendships.findBetween(actorId, toBlock).ifPresent(friendships::delete);
    }

    public void unblock(UUID actorId, UUID toUnblock) {
        blocks.findByBlockerIdAndBlockedId(actorId, toUnblock).ifPresent(blocks::delete);
    }

    public List<SearchHit> blockedList(UUID actorId) {
        return blocks.findByBlockerId(actorId).stream()
            .map(b -> users.findById(b.getBlockedId()).map(u -> new SearchHit(u.getId(), u.getUsername())))
            .flatMap(Optional::stream)
            .collect(Collectors.toList());
    }
}
```

- [ ] Verify: `mvn compile 2>&1 | tail -5` BUILD SUCCESS (will fail compilation on `FriendsBroadcaster` — written in next task)

### Task B2: FriendsBroadcaster (WS push)

**File:** `src/main/java/com/remi/friends/FriendsBroadcaster.java`

```java
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
```

- [ ] Verify: `mvn compile 2>&1 | tail -5` BUILD SUCCESS
- [ ] Commit: `git add src/main/java/com/remi/friends/FriendsService.java src/main/java/com/remi/friends/FriendsBroadcaster.java && git commit -m "feat(friends): FriendsService + WS FriendsBroadcaster"`

### Task B3: FriendsController + UsersSearchController + tests

**File:** `src/main/java/com/remi/friends/FriendsController.java`

```java
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
```

**File:** `src/main/java/com/remi/friends/UsersBlockController.java`

```java
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
```

**File:** `src/main/java/com/remi/friends/UsersSearchController.java`

```java
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
```

**Note:** Add to `UserRepository`: `List<UserEntity> findTop20ByUsernameNormalizedStartingWith(String prefix);` if not present. If `UserEntity` uses a different field name, grep first.

**File:** `src/test/java/com/remi/friends/FriendsControllerTest.java` — integration test with 6 cases:
- send request creates row
- can't send to self (400)
- accept changes status to ACCEPTED
- only addressee can accept (403/SecurityException)
- list returns accepted friends with online flag false (presence empty)
- unfriend removes row

```java
package com.remi.friends;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remi.auth.api.dto.LoginRequest;
import com.remi.auth.api.dto.RegisterRequest;
import com.remi.auth.api.dto.AuthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FriendsControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired FriendshipRepository friendships;
    @Autowired com.remi.user.persistence.UserRepository userRepo;
    @Autowired com.remi.auth.password.PasswordHasher hasher;
    @Autowired com.remi.auth.mail.MailService mail;

    // Helper: register + login a user, return JWT
    // NOTE: adapt to project's actual auth flow (see DeviceTokenControllerTest pattern from 5b)
    // The exact register/verify/login chain is reused.

    @Test
    void sendRequestCreatesPendingFriendship() throws Exception {
        // (See implementer note below — fill in helper that mirrors DeviceTokenControllerTest)
    }
}
```

**Implementer note for B3 test:** Mirror the pattern from `src/test/java/com/remi/push/DeviceTokenControllerTest.java` (Stage 5b) for register→verify→login JWT minting. Write tests in a similar style.

- [ ] Verify compilation: `mvn compile 2>&1 | tail -5` BUILD SUCCESS
- [ ] Run tests: `mvn test 2>&1 | tail -10` — total 196 + new tests (target 204+)
- [ ] Commit: `git add src/main/java/com/remi/friends src/test/java/com/remi/friends src/main/java/com/remi/user/persistence/UserRepository.java && git commit -m "feat(friends): controllers + tests for friends/blocks/search"`

---

## Phase C — Presence (WS lifecycle hook)

### Task C1: WebSocket connect/disconnect listener

**Modify:** find existing `@EventListener` / `WebSocketHandler` in `src/main/java/com/remi/ws/` — likely a `SessionDisconnectEventListener` or in `WebSocketConfig`.

Search: `grep -rn "SessionConnectEvent\|SessionDisconnectEvent\|@EventListener.*Session" src/main/java/`

**New file (if none exist):** `src/main/java/com/remi/ws/presence/PresenceEventListener.java`

```java
package com.remi.ws.presence;

import com.remi.friends.FriendshipRepository;
import com.remi.friends.FriendshipStatus;
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

    public PresenceEventListener(PresenceRegistry presence, FriendshipRepository friendships, FriendsBroadcaster broadcaster) {
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
```

- [ ] Verify: `mvn compile 2>&1 | tail -5` BUILD SUCCESS
- [ ] Run full backend suite: `mvn test 2>&1 | tail -10` — no regressions (WS lifecycle tests may need updating to mock these dependencies; if so, add `@MockBean PresenceRegistry`)
- [ ] Commit: `git add src/main/java/com/remi/ws/presence && git commit -m "feat(presence): WS connect/disconnect updates registry + notifies friends"`

---

## Phase D — Frontend: NgRx feature

### Task D1: friends models + actions

**File:** `frontend/src/app/store/friends/friends.models.ts`

```ts
export interface Friend {
  id: string;
  username: string;
  online: boolean;
  since?: string;
}

export interface FriendRequest {
  id: number;
  userId: string;
  username: string;
  createdAt: string;
}

export interface UserSearchHit {
  id: string;
  username: string;
}
```

**File:** `frontend/src/app/store/friends/friends.actions.ts`

```ts
import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { Friend, FriendRequest, UserSearchHit } from './friends.models';

export const FriendsActions = createActionGroup({
  source: 'Friends',
  events: {
    'Load Friends': emptyProps(),
    'Friends Loaded': props<{ friends: Friend[] }>(),
    'Friends Load Failed': props<{ error: string }>(),

    'Load Requests': emptyProps(),
    'Requests Loaded': props<{ incoming: FriendRequest[]; outgoing: FriendRequest[] }>(),

    'Search Users': props<{ q: string }>(),
    'Search Results': props<{ hits: UserSearchHit[] }>(),
    'Search Cleared': emptyProps(),

    'Send Request': props<{ addresseeId: string }>(),
    'Request Sent': props<{ id: number }>(),

    'Accept Request': props<{ id: number }>(),
    'Reject Request': props<{ id: number }>(),
    'Cancel Request': props<{ id: number }>(),
    'Unfriend': props<{ friendId: string }>(),

    'Block User': props<{ userId: string }>(),
    'Unblock User': props<{ userId: string }>(),

    'Presence Updated': props<{ userId: string; online: boolean; since?: string }>(),
    'Friend Request Received': props<{ requestId: number; fromUserId: string; fromUsername: string }>(),
    'Friend Request Accepted': props<{ requestId: number }>(),
  },
});
```

**File:** `frontend/src/app/store/friends/friends.reducer.ts`

```ts
import { createFeature, createReducer, on } from '@ngrx/store';
import { Friend, FriendRequest, UserSearchHit } from './friends.models';
import { FriendsActions } from './friends.actions';

interface FriendsState {
  friends: Friend[];
  incoming: FriendRequest[];
  outgoing: FriendRequest[];
  searchHits: UserSearchHit[];
  error: string | null;
}

const initial: FriendsState = {
  friends: [],
  incoming: [],
  outgoing: [],
  searchHits: [],
  error: null,
};

export const friendsFeature = createFeature({
  name: 'friends',
  reducer: createReducer(
    initial,
    on(FriendsActions.friendsLoaded, (s, { friends }) => ({ ...s, friends, error: null })),
    on(FriendsActions.friendsLoadFailed, (s, { error }) => ({ ...s, error })),
    on(FriendsActions.requestsLoaded, (s, { incoming, outgoing }) => ({ ...s, incoming, outgoing })),
    on(FriendsActions.searchResults, (s, { hits }) => ({ ...s, searchHits: hits })),
    on(FriendsActions.searchCleared, (s) => ({ ...s, searchHits: [] })),
    on(FriendsActions.presenceUpdated, (s, { userId, online, since }) => ({
      ...s,
      friends: s.friends.map(f => f.id === userId ? { ...f, online, since } : f),
    })),
    on(FriendsActions.friendRequestAccepted, (s, { requestId }) => ({
      ...s,
      outgoing: s.outgoing.filter(r => r.id !== requestId),
    })),
  ),
});
```

- [ ] Verify build: `npx ng build --configuration=development 2>&1 | tail -5` exit 0
- [ ] Commit: `git add frontend/src/app/store/friends && git commit -m "feat(friends): NgRx models + actions + reducer"`

### Task D2: friends API + effects

**File:** `frontend/src/app/core/api/friends.api.ts`

```ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../config/api-url.tokens';
import { Friend, FriendRequest, UserSearchHit } from '../../store/friends/friends.models';

@Injectable({ providedIn: 'root' })
export class FriendsApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_URL);

  listFriends(): Promise<Friend[]> {
    return firstValueFrom(this.http.get<Friend[]>(`${this.base}/friends`));
  }
  listRequests(): Promise<{ incoming: FriendRequest[]; outgoing: FriendRequest[] }> {
    return firstValueFrom(this.http.get<any>(`${this.base}/friends/requests`));
  }
  searchUsers(q: string): Promise<UserSearchHit[]> {
    return firstValueFrom(this.http.get<UserSearchHit[]>(`${this.base}/users/search?q=${encodeURIComponent(q)}`));
  }
  sendRequest(addresseeId: string): Promise<{ id: number }> {
    return firstValueFrom(this.http.post<{ id: number }>(`${this.base}/friends/requests`, { addresseeId }));
  }
  accept(id: number): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${this.base}/friends/requests/${id}/accept`, {})).then(() => undefined);
  }
  reject(id: number): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${this.base}/friends/requests/${id}/reject`, {})).then(() => undefined);
  }
  cancel(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base}/friends/requests/${id}`)).then(() => undefined);
  }
  unfriend(friendId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base}/friends/${friendId}`)).then(() => undefined);
  }
  block(userId: string): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${this.base}/users/${userId}/block`, {})).then(() => undefined);
  }
  unblock(userId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base}/users/${userId}/block`)).then(() => undefined);
  }
}
```

**File:** `frontend/src/app/store/friends/friends.effects.ts`

```ts
import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { from, of } from 'rxjs';
import { catchError, map, mergeMap, switchMap } from 'rxjs/operators';
import { FriendsActions } from './friends.actions';
import { FriendsApi } from '../../core/api/friends.api';

@Injectable()
export class FriendsEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(FriendsApi);

  loadFriends$ = createEffect(() =>
    this.actions$.pipe(
      ofType(FriendsActions.loadFriends),
      switchMap(() => from(this.api.listFriends()).pipe(
        map(friends => FriendsActions.friendsLoaded({ friends })),
        catchError(err => of(FriendsActions.friendsLoadFailed({ error: err?.message ?? 'unknown' }))),
      )),
    ));

  loadRequests$ = createEffect(() =>
    this.actions$.pipe(
      ofType(FriendsActions.loadRequests),
      switchMap(() => from(this.api.listRequests()).pipe(
        map(({ incoming, outgoing }) => FriendsActions.requestsLoaded({ incoming, outgoing })),
      )),
    ));

  search$ = createEffect(() =>
    this.actions$.pipe(
      ofType(FriendsActions.searchUsers),
      switchMap(({ q }) => from(this.api.searchUsers(q)).pipe(
        map(hits => FriendsActions.searchResults({ hits })),
      )),
    ));

  sendRequest$ = createEffect(() =>
    this.actions$.pipe(
      ofType(FriendsActions.sendRequest),
      mergeMap(({ addresseeId }) => from(this.api.sendRequest(addresseeId)).pipe(
        map(({ id }) => FriendsActions.requestSent({ id })),
      )),
    ));

  accept$ = createEffect(() =>
    this.actions$.pipe(
      ofType(FriendsActions.acceptRequest),
      mergeMap(({ id }) => from(this.api.accept(id)).pipe(
        map(() => FriendsActions.loadFriends()),
      )),
    ));

  reject$ = createEffect(() =>
    this.actions$.pipe(
      ofType(FriendsActions.rejectRequest),
      mergeMap(({ id }) => from(this.api.reject(id)).pipe(
        map(() => FriendsActions.loadRequests()),
      )),
    ));

  cancel$ = createEffect(() =>
    this.actions$.pipe(
      ofType(FriendsActions.cancelRequest),
      mergeMap(({ id }) => from(this.api.cancel(id)).pipe(
        map(() => FriendsActions.loadRequests()),
      )),
    ));

  unfriend$ = createEffect(() =>
    this.actions$.pipe(
      ofType(FriendsActions.unfriend),
      mergeMap(({ friendId }) => from(this.api.unfriend(friendId)).pipe(
        map(() => FriendsActions.loadFriends()),
      )),
    ));

  block$ = createEffect(() =>
    this.actions$.pipe(
      ofType(FriendsActions.blockUser),
      mergeMap(({ userId }) => from(this.api.block(userId)).pipe(
        map(() => FriendsActions.loadFriends()),
      )),
    ));

  unblock$ = createEffect(() =>
    this.actions$.pipe(
      ofType(FriendsActions.unblockUser),
      mergeMap(({ userId }) => from(this.api.unblock(userId)).pipe(
        map(() => FriendsActions.loadFriends()),
      )),
    ));
}
```

- [ ] Register the feature + effects in `app.config.ts` providers: `provideState(friendsFeature)` and `provideEffects(FriendsEffects)`.
- [ ] Verify build exit 0.
- [ ] Commit: `git add frontend/src/app/core/api/friends.api.ts frontend/src/app/store/friends/friends.effects.ts frontend/src/app/app.config.ts && git commit -m "feat(friends): FriendsApi + NgRx effects + register feature"`

### Task D3: WS subscription bridge — push WS events into NgRx

**File:** `frontend/src/app/core/ws/friends-ws.bridge.ts`

```ts
import { Injectable, inject, OnDestroy } from '@angular/core';
import { Store } from '@ngrx/store';
import { Subscription } from 'rxjs';
import { StompService } from './stomp.service';
import { FriendsActions } from '../../store/friends/friends.actions';

@Injectable({ providedIn: 'root' })
export class FriendsWsBridge implements OnDestroy {
  private readonly stomp = inject(StompService);
  private readonly store = inject(Store);
  private subs: Subscription[] = [];

  async start(): Promise<void> {
    await this.stomp.ensureConnected();
    this.subs.push(this.stomp.subscribe('/user/queue/presence').subscribe((msg: any) => {
      this.store.dispatch(FriendsActions.presenceUpdated({ userId: msg.userId, online: msg.online, since: msg.since }));
    }));
    this.subs.push(this.stomp.subscribe('/user/queue/friend-requests').subscribe((msg: any) => {
      if (msg.type === 'incoming') {
        this.store.dispatch(FriendsActions.friendRequestReceived({ requestId: msg.requestId, fromUserId: msg.fromUserId, fromUsername: msg.fromUsername }));
        this.store.dispatch(FriendsActions.loadRequests());
      } else if (msg.type === 'accepted') {
        this.store.dispatch(FriendsActions.friendRequestAccepted({ requestId: msg.requestId }));
        this.store.dispatch(FriendsActions.loadFriends());
      }
    }));
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
  }
}
```

**Note:** `StompService.subscribe(destination)` and `StompService.ensureConnected()` must exist. If not, grep existing usage in `frontend/src/app/store/game/game.effects.ts` (or wherever WebSocket subscriptions happen today) and adapt to existing pattern. If existing services use a different shape, use that instead.

- [ ] Bootstrap `FriendsWsBridge.start()` from `AppComponent.ngOnInit` (alongside DeepLinkService.init()).
- [ ] Verify build exit 0.
- [ ] Commit: `git add frontend/src/app/core/ws/friends-ws.bridge.ts frontend/src/app/app.component.ts && git commit -m "feat(friends): WS bridge for presence + friend request events"`

---

## Phase E — Frontend pages

### Task E1: FriendsHomePage

**Files (under `frontend/src/app/features/friends/`):**

`friends-home.page.ts`:
```ts
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { IonContent, IonHeader, IonToolbar, IonTitle, IonList, IonItem, IonLabel, IonIcon, IonButton, IonBadge } from '@ionic/angular/standalone';
import { FriendsActions } from '../../store/friends/friends.actions';
import { friendsFeature } from '../../store/friends/friends.reducer';

@Component({
  selector: 'app-friends-home',
  standalone: true,
  imports: [CommonModule, RouterLink, IonContent, IonHeader, IonToolbar, IonTitle, IonList, IonItem, IonLabel, IonIcon, IonButton, IonBadge],
  template: `
    <ion-header><ion-toolbar><ion-title>Prieteni</ion-title></ion-toolbar></ion-header>
    <ion-content>
      <ion-list>
        <ion-item routerLink="/friends/search"><ion-label>Caută prieteni</ion-label></ion-item>
        <ion-item routerLink="/friends/requests">
          <ion-label>Cereri</ion-label>
          <ion-badge *ngIf="(incoming$ | async)?.length" slot="end">{{ (incoming$ | async)?.length }}</ion-badge>
        </ion-item>
        <ion-item routerLink="/friends/blocked"><ion-label>Blocați</ion-label></ion-item>
      </ion-list>

      <ion-list>
        <ion-item *ngFor="let f of (friends$ | async)">
          <ion-label>
            <h2>{{ f.username }}</h2>
            <p>{{ f.online ? 'online' : 'offline' }}</p>
          </ion-label>
          <ion-button slot="end" (click)="invite(f.id)">Invită</ion-button>
        </ion-item>
      </ion-list>
    </ion-content>
  `,
})
export class FriendsHomePage implements OnInit {
  private readonly store = inject(Store);
  readonly friends$ = this.store.select(friendsFeature.selectFriends);
  readonly incoming$ = this.store.select(friendsFeature.selectIncoming);

  ngOnInit(): void {
    this.store.dispatch(FriendsActions.loadFriends());
    this.store.dispatch(FriendsActions.loadRequests());
  }

  invite(friendId: string): void {
    // TODO Stage 7+: trigger invite via POST /api/friends/{id}/invite — leave as placeholder for now
    console.log('invite', friendId);
  }
}
```

- [ ] Add route in `app.routes.ts`: `{ path: 'friends', loadComponent: () => import('./features/friends/friends-home.page').then(m => m.FriendsHomePage) }`
- [ ] Commit: `git add frontend/src/app/features/friends/friends-home.page.ts frontend/src/app/app.routes.ts && git commit -m "feat(friends): FriendsHomePage with list + requests badge"`

### Task E2: FriendSearchPage + FriendRequestsPage + BlockedListPage

**Files:**

`friend-search.page.ts` — input with debounced search, list results with "Add" button → dispatch `sendRequest`. 
`friend-requests.page.ts` — two ion-segments (incoming / outgoing); accept/reject for incoming; cancel for outgoing.
`blocked-list.page.ts` — list of blocked users; unblock button.

These follow the same pattern as `friends-home.page.ts`. Write them following the same component skeleton, dispatching actions from `FriendsActions` and selecting from `friendsFeature`.

Detail implementation for `friend-search.page.ts`:

```ts
import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Store } from '@ngrx/store';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { IonContent, IonHeader, IonToolbar, IonTitle, IonSearchbar, IonList, IonItem, IonLabel, IonButton } from '@ionic/angular/standalone';
import { FriendsActions } from '../../store/friends/friends.actions';
import { friendsFeature } from '../../store/friends/friends.reducer';

@Component({
  selector: 'app-friend-search',
  standalone: true,
  imports: [CommonModule, FormsModule, IonContent, IonHeader, IonToolbar, IonTitle, IonSearchbar, IonList, IonItem, IonLabel, IonButton],
  template: `
    <ion-header><ion-toolbar><ion-title>Caută prieteni</ion-title></ion-toolbar></ion-header>
    <ion-content>
      <ion-searchbar [(ngModel)]="q" (ionInput)="onInput()" placeholder="username..."></ion-searchbar>
      <ion-list>
        <ion-item *ngFor="let hit of (hits$ | async)">
          <ion-label>{{ hit.username }}</ion-label>
          <ion-button slot="end" (click)="send(hit.id)">Adaugă</ion-button>
        </ion-item>
      </ion-list>
    </ion-content>
  `,
})
export class FriendSearchPage {
  private readonly store = inject(Store);
  readonly hits$ = this.store.select(friendsFeature.selectSearchHits);
  q = '';
  private readonly input$ = new Subject<string>();

  constructor() {
    this.input$.pipe(debounceTime(250), distinctUntilChanged()).subscribe(q => {
      if (q.length >= 2) this.store.dispatch(FriendsActions.searchUsers({ q }));
      else this.store.dispatch(FriendsActions.searchCleared());
    });
  }

  onInput(): void { this.input$.next(this.q); }
  send(id: string): void { this.store.dispatch(FriendsActions.sendRequest({ addresseeId: id })); this.q = ''; this.store.dispatch(FriendsActions.searchCleared()); }
}
```

`friend-requests.page.ts`:

```ts
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Store } from '@ngrx/store';
import { IonContent, IonHeader, IonToolbar, IonTitle, IonList, IonItem, IonLabel, IonButton, IonSegment, IonSegmentButton } from '@ionic/angular/standalone';
import { FormsModule } from '@angular/forms';
import { FriendsActions } from '../../store/friends/friends.actions';
import { friendsFeature } from '../../store/friends/friends.reducer';

@Component({
  selector: 'app-friend-requests',
  standalone: true,
  imports: [CommonModule, FormsModule, IonContent, IonHeader, IonToolbar, IonTitle, IonList, IonItem, IonLabel, IonButton, IonSegment, IonSegmentButton],
  template: `
    <ion-header><ion-toolbar><ion-title>Cereri</ion-title></ion-toolbar></ion-header>
    <ion-content>
      <ion-segment [(ngModel)]="tab">
        <ion-segment-button value="in"><ion-label>Primite</ion-label></ion-segment-button>
        <ion-segment-button value="out"><ion-label>Trimise</ion-label></ion-segment-button>
      </ion-segment>
      <ion-list *ngIf="tab === 'in'">
        <ion-item *ngFor="let r of (incoming$ | async)">
          <ion-label>{{ r.username }}</ion-label>
          <ion-button slot="end" (click)="accept(r.id)">Acceptă</ion-button>
          <ion-button slot="end" color="medium" (click)="reject(r.id)">Refuză</ion-button>
        </ion-item>
      </ion-list>
      <ion-list *ngIf="tab === 'out'">
        <ion-item *ngFor="let r of (outgoing$ | async)">
          <ion-label>{{ r.username }}</ion-label>
          <ion-button slot="end" color="medium" (click)="cancel(r.id)">Anulează</ion-button>
        </ion-item>
      </ion-list>
    </ion-content>
  `,
})
export class FriendRequestsPage implements OnInit {
  private readonly store = inject(Store);
  readonly incoming$ = this.store.select(friendsFeature.selectIncoming);
  readonly outgoing$ = this.store.select(friendsFeature.selectOutgoing);
  tab: 'in' | 'out' = 'in';

  ngOnInit(): void { this.store.dispatch(FriendsActions.loadRequests()); }
  accept(id: number): void { this.store.dispatch(FriendsActions.acceptRequest({ id })); }
  reject(id: number): void { this.store.dispatch(FriendsActions.rejectRequest({ id })); }
  cancel(id: number): void { this.store.dispatch(FriendsActions.cancelRequest({ id })); }
}
```

`blocked-list.page.ts`:

```ts
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Store } from '@ngrx/store';
import { IonContent, IonHeader, IonToolbar, IonTitle, IonList, IonItem, IonLabel, IonButton } from '@ionic/angular/standalone';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../../core/config/api-url.tokens';
import { inject as _inject } from '@angular/core';
import { FriendsActions } from '../../store/friends/friends.actions';

@Component({
  selector: 'app-blocked-list',
  standalone: true,
  imports: [CommonModule, IonContent, IonHeader, IonToolbar, IonTitle, IonList, IonItem, IonLabel, IonButton],
  template: `
    <ion-header><ion-toolbar><ion-title>Blocați</ion-title></ion-toolbar></ion-header>
    <ion-content>
      <ion-list>
        <ion-item *ngFor="let b of blocked">
          <ion-label>{{ b.username }}</ion-label>
          <ion-button slot="end" (click)="unblock(b.id)">Deblochează</ion-button>
        </ion-item>
      </ion-list>
    </ion-content>
  `,
})
export class BlockedListPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_URL);
  private readonly store = inject(Store);
  blocked: { id: string; username: string }[] = [];

  async ngOnInit(): Promise<void> {
    this.blocked = await firstValueFrom(this.http.get<{ id: string; username: string }[]>(`${this.base}/users/blocked`));
  }

  async unblock(id: string): Promise<void> {
    this.store.dispatch(FriendsActions.unblockUser({ userId: id }));
    this.blocked = this.blocked.filter(b => b.id !== id);
  }
}
```

- [ ] Add 3 routes in `app.routes.ts`:
```ts
{ path: 'friends/search', loadComponent: () => import('./features/friends/friend-search.page').then(m => m.FriendSearchPage) },
{ path: 'friends/requests', loadComponent: () => import('./features/friends/friend-requests.page').then(m => m.FriendRequestsPage) },
{ path: 'friends/blocked', loadComponent: () => import('./features/friends/blocked-list.page').then(m => m.BlockedListPage) },
```
- [ ] Add a button in `lobby-home.page.html` (or `.ts` template): `<ion-button routerLink="/friends">Prieteni</ion-button>`
- [ ] Verify build exit 0 + Karma suite passes (no new tests yet; existing 147 must stay green)
- [ ] Commit: `git add frontend/src/app/features/friends frontend/src/app/app.routes.ts frontend/src/app/features/lobby && git commit -m "feat(friends): search + requests + blocked list pages with routes"`

---

## Phase F — Invite to game

### Task F1: Backend invite endpoint

**Modify:** `src/main/java/com/remi/lobby/api/LobbyController.java` (or wherever private-match creation lives — likely a `MatchController`). Add endpoint:

```java
@PostMapping("/api/friends/{friendId}/invite")
public ResponseEntity<Map<String, Object>> invite(@AuthenticationPrincipal UUID actorId, @PathVariable UUID friendId, @RequestBody MatchSettings settings) {
    // 1. Verify friendship exists & ACCEPTED
    // 2. Create private match (use existing matchService.createPrivate(actorId, settings))
    // 3. Push WS to friend
    // 4. Return code + matchId
}
```

This task requires examining existing private-match flow. **Implementer:** grep `private.*match\|join.*code\|joinByCode` in `src/main/java/com/remi/lobby/`. Reuse the existing match-create method; bolt on friendship check + WS push.

- [ ] Commit when working: `git commit -m "feat(friends): POST /api/friends/{friendId}/invite creates private match + pushes WS invite"`

### Task F2: Frontend invite hookup

**Modify:** `friends-home.page.ts` `invite()` method — call backend, on success deep-link the inviter to `/lobby/quick-match/<code>` (they already created the match, they're waiting in lobby). The invited friend receives the WS push and gets a notification/banner.

**Modify:** Bridge in `FriendsWsBridge` to subscribe `/user/queue/invites` (already partial — extend to handle friend-invite type by navigating to `/lobby/join-by-code?code=<>`).

- [ ] Commit: `git commit -m "feat(friends): invite friend to private match via UI"`

---

## Phase G — Docs

### Task G1: Update README + spec link

Add to `frontend/README.md` and root `README.md` (if exists) a section on friends + presence.

- [ ] Commit: `git commit -m "docs(stage6): friends + presence overview"`
