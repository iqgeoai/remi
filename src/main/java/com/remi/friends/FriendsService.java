package com.remi.friends;

import com.remi.user.persistence.UserRepository;
import com.remi.user.persistence.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

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
            .toList();
    }

    public List<RequestDto> incoming(UUID userId) {
        return friendships.findByAddresseeIdAndStatus(userId, FriendshipStatus.PENDING).stream()
            .map(f -> users.findById(f.getRequesterId()).map(u -> new RequestDto(f.getId(), u.getId(), u.getUsername(), f.getCreatedAt())))
            .flatMap(Optional::stream)
            .toList();
    }

    public List<RequestDto> outgoing(UUID userId) {
        return friendships.findByRequesterIdAndStatus(userId, FriendshipStatus.PENDING).stream()
            .map(f -> users.findById(f.getAddresseeId()).map(u -> new RequestDto(f.getId(), u.getId(), u.getUsername(), f.getCreatedAt())))
            .flatMap(Optional::stream)
            .toList();
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
            .toList();
    }
}
