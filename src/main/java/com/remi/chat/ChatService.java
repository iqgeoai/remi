package com.remi.chat;

import com.remi.friends.FriendshipRepository;
import com.remi.friends.FriendshipStatus;
import com.remi.friends.UserBlockRepository;
import com.remi.lobby.persistence.GamePlayerRepository;
import com.remi.persistence.GameRepository;
import com.remi.user.persistence.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ChatService {
    private final ChatMessageRepository repo;
    private final FriendshipRepository friendships;
    private final UserBlockRepository blocks;
    private final UserRepository users;
    private final GameRepository games;
    private final GamePlayerRepository gamePlayers;
    private final ChatRateLimiter limiter;
    private final ChatBroadcaster broadcaster;

    public ChatService(ChatMessageRepository repo,
                       FriendshipRepository friendships,
                       UserBlockRepository blocks,
                       UserRepository users,
                       GameRepository games,
                       GamePlayerRepository gamePlayers,
                       ChatRateLimiter limiter,
                       ChatBroadcaster broadcaster) {
        this.repo = repo;
        this.friendships = friendships;
        this.blocks = blocks;
        this.users = users;
        this.games = games;
        this.gamePlayers = gamePlayers;
        this.limiter = limiter;
        this.broadcaster = broadcaster;
    }

    public record MessageDto(Long id, UUID senderId, String senderUsername, String body, java.time.Instant createdAt) {}
    public record ConversationDto(UUID otherUserId, String otherUsername, java.time.Instant lastMessageAt, String lastBody) {}

    public List<MessageDto> matchHistory(UUID actorId, UUID matchId, int limit) {
        assertMatchParticipant(actorId, matchId);
        return toDtos(repo.findRecent(ChannelType.MATCH, matchId.toString(), PageRequest.of(0, Math.min(limit, 200))));
    }

    public Long sendMatch(UUID actorId, UUID matchId, String body) {
        assertMatchParticipant(actorId, matchId);
        String channelKey = matchId.toString();
        if (!limiter.allow(actorId, channelKey)) throw new RateLimitException();
        String trimmed = validateBody(body);
        ChatMessage saved = repo.save(new ChatMessage(ChannelType.MATCH, channelKey, actorId, trimmed));
        broadcaster.broadcastMatch(matchId, saved, usernameOf(actorId));
        return saved.getId();
    }

    public List<MessageDto> dmHistory(UUID actorId, UUID otherId, int limit) {
        assertFriendsAndNotBlocked(actorId, otherId);
        return toDtos(repo.findRecent(ChannelType.DM, dmChannelKey(actorId, otherId), PageRequest.of(0, Math.min(limit, 200))));
    }

    public Long sendDm(UUID actorId, UUID otherId, String body) {
        assertFriendsAndNotBlocked(actorId, otherId);
        String channelKey = dmChannelKey(actorId, otherId);
        if (!limiter.allow(actorId, channelKey)) throw new RateLimitException();
        String trimmed = validateBody(body);
        ChatMessage saved = repo.save(new ChatMessage(ChannelType.DM, channelKey, actorId, trimmed));
        broadcaster.broadcastDm(actorId, otherId, saved, usernameOf(actorId));
        return saved.getId();
    }

    public List<ConversationDto> listConversations(UUID actorId) {
        // Match any DM channel containing this user
        String pattern = "%" + actorId + "%";
        return repo.findDmConversations(pattern).stream()
            .map(m -> {
                String[] parts = m.getChannelKey().split(":");
                UUID otherId = parts[0].equals(actorId.toString()) ? UUID.fromString(parts[1]) : UUID.fromString(parts[0]);
                return users.findById(otherId).map(u -> new ConversationDto(otherId, u.getUsername(), m.getCreatedAt(), m.getBody()));
            })
            .flatMap(Optional::stream)
            .collect(Collectors.toList());
    }

    // -- helpers --

    private String validateBody(String body) {
        if (body == null) throw new IllegalArgumentException("body required");
        String t = body.trim();
        if (t.isEmpty()) throw new IllegalArgumentException("body empty");
        if (t.length() > 500) throw new IllegalArgumentException("body too long");
        return t;
    }

    private String dmChannelKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    /**
     * Verifies {@code actorId} is seated in {@code matchId}. Backed by
     * {@link GamePlayerRepository#existsByGameIdAndUserId}. Existence of the
     * game itself is implied — if no row exists in {@code game_players} for
     * (matchId, actorId) the user cannot be a participant. We still load the
     * game to give a clearer error when the match does not exist.
     */
    private void assertMatchParticipant(UUID actorId, UUID matchId) {
        if (!games.existsById(matchId)) {
            throw new IllegalArgumentException("Match not found");
        }
        if (!gamePlayers.existsByGameIdAndUserId(matchId, actorId)) {
            throw new SecurityException("Not a match participant");
        }
    }

    private void assertFriendsAndNotBlocked(UUID a, UUID b) {
        if (blocks.existsByBlockerIdAndBlockedId(a, b) || blocks.existsByBlockerIdAndBlockedId(b, a)) {
            throw new SecurityException("Blocked");
        }
        var f = friendships.findBetween(a, b).orElseThrow(() -> new SecurityException("Not friends"));
        if (f.getStatus() != FriendshipStatus.ACCEPTED) throw new SecurityException("Not friends");
    }

    private String usernameOf(UUID id) {
        return users.findById(id).map(u -> u.getUsername()).orElse("?");
    }

    private List<MessageDto> toDtos(List<ChatMessage> msgs) {
        // Reverse so caller gets oldest-first
        Collections.reverse(msgs);
        return msgs.stream()
            .map(m -> new MessageDto(m.getId(), m.getSenderId(), usernameOf(m.getSenderId()), m.getBody(), m.getCreatedAt()))
            .collect(Collectors.toList());
    }

    public static class RateLimitException extends RuntimeException {}
}
