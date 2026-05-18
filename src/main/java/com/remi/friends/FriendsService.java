package com.remi.friends;

import com.remi.engine.domain.Difficulty;
import com.remi.engine.domain.Mode;
import com.remi.lobby.domain.LobbyGame;
import com.remi.lobby.service.LobbyService;
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
    private final LobbyService lobby;

    public FriendsService(FriendshipRepository friendships, UserBlockRepository blocks,
                          UserRepository users, PresenceRegistry presence, FriendsBroadcaster broadcaster,
                          LobbyService lobby) {
        this.friendships = friendships;
        this.blocks = blocks;
        this.users = users;
        this.presence = presence;
        this.broadcaster = broadcaster;
        this.lobby = lobby;
    }

    public record FriendDto(UUID id, String username, boolean online, Instant since) {}
    public record RequestDto(Long id, UUID userId, String username, Instant createdAt) {}
    public record SearchHit(UUID id, String username) {}
    public record InviteResult(String code, UUID matchId) {}

    /**
     * Settings the inviter passes for the private match they create when inviting
     * a friend. All fields have safe defaults so the frontend can post an empty
     * body and still get a sensible 2-player ETALAT/MED game.
     */
    public record InviteSettings(Integer numPlayers, Mode mode, Difficulty difficulty) {
        public int numPlayersOrDefault() { return numPlayers != null ? numPlayers : 2; }
        public Mode modeOrDefault() { return mode != null ? mode : Mode.ETALAT; }
        public Difficulty difficultyOrDefault() { return difficulty != null ? difficulty : Difficulty.MED; }
    }

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

    /**
     * Creates a private match owned by {@code actorId} and pushes a WS invite
     * to {@code friendId}. Requires an ACCEPTED friendship between the two —
     * otherwise throws {@link IllegalStateException}. The inviter is auto-seated
     * by {@link LobbyService#createPrivate}; the friend joins later via the
     * returned code (typically through the WS push deep-link).
     */
    public InviteResult invite(UUID actorId, UUID friendId, InviteSettings settings) {
        Friendship f = friendships.findBetween(actorId, friendId)
            .orElseThrow(() -> new IllegalStateException("Not friends"));
        if (f.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new IllegalStateException("Not friends");
        }
        InviteSettings s = settings != null ? settings : new InviteSettings(null, null, null);
        LobbyGame game = lobby.createPrivate(
            actorId,
            s.numPlayersOrDefault(),
            s.modeOrDefault(),
            s.difficultyOrDefault());
        String fromUsername = users.findById(actorId).map(UserEntity::getUsername).orElse("");
        broadcaster.notifyFriendInvite(friendId, actorId, fromUsername, game.joinCode(), game.id());
        return new InviteResult(game.joinCode(), game.id());
    }
}
