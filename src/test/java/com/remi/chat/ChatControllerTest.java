package com.remi.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remi.engine.domain.Difficulty;
import com.remi.engine.domain.Mode;
import com.remi.lobby.domain.LobbyGame;
import com.remi.lobby.service.LobbyService;
import com.remi.friends.Friendship;
import com.remi.friends.FriendshipRepository;
import com.remi.friends.FriendshipStatus;
import com.remi.friends.UserBlock;
import com.remi.friends.UserBlockRepository;
import com.remi.user.api.MockMailServiceTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(MockMailServiceTestConfig.class)
class ChatControllerTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;
    @Autowired LobbyService lobbyService;
    @Autowired FriendshipRepository friendships;
    @Autowired UserBlockRepository blocks;
    @Autowired ChatMessageRepository chatRepo;
    @Autowired ChatRateLimiter rateLimiter;

    @BeforeEach
    void reset() {
        MockMailServiceTestConfig.SENT.clear();
        jdbc.execute("TRUNCATE chat_messages, user_blocks, friendships, device_tokens, game_players, games, refresh_tokens, verification_tokens, password_reset_tokens, users CASCADE");
        rateLimiter.clearAllForTest();
    }

    private record Account(UUID id, String jwt) {}

    private Account registerVerifyLogin(String email, String username) throws Exception {
        String regBody = String.format(
            "{\"email\":\"%s\",\"username\":\"%s\",\"password\":\"passwordxx\"}", email, username);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(regBody))
            .andExpect(status().isCreated());
        var token = MockMailServiceTestConfig.SENT.get(MockMailServiceTestConfig.SENT.size() - 1).token();
        String verifyBody = String.format("{\"token\":\"%s\"}", token);
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content(verifyBody))
            .andExpect(status().isNoContent());
        String loginBody = String.format(
            "{\"emailOrUsername\":\"%s\",\"password\":\"passwordxx\"}", username);
        String loginResp = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String jwt = om.readTree(loginResp).get("accessToken").asText();
        UUID id = jdbc.queryForObject(
            "SELECT id FROM users WHERE username_normalized = ?",
            UUID.class, username.toLowerCase());
        return new Account(id, jwt);
    }

    private void becomeFriends(UUID a, UUID b) {
        Friendship f = new Friendship(a, b);
        f.setStatus(FriendshipStatus.ACCEPTED);
        friendships.save(f);
    }

    @Test
    void matchHistoryRejectsNonParticipant() throws Exception {
        Account alice = registerVerifyLogin("alice@example.com", "alice");
        Account bob = registerVerifyLogin("bob@example.com", "bob");
        Account carol = registerVerifyLogin("carol@example.com", "carol");

        // alice creates a private match, bob joins; carol is NOT a participant
        LobbyGame g = lobbyService.createPrivate(alice.id(), 3, Mode.ETALAT, Difficulty.MED);
        lobbyService.joinByCode(bob.id(), g.joinCode());

        mvc.perform(get("/api/chat/match/" + g.id())
                .header("Authorization", "Bearer " + carol.jwt()))
            .andExpect(status().is5xxServerError());
    }

    @Test
    void matchSendBroadcastsAndPersists() throws Exception {
        Account alice = registerVerifyLogin("alice@example.com", "alice");
        Account bob = registerVerifyLogin("bob@example.com", "bob");
        LobbyGame g = lobbyService.createPrivate(alice.id(), 3, Mode.ETALAT, Difficulty.MED);
        lobbyService.joinByCode(bob.id(), g.joinCode());

        mvc.perform(post("/api/chat/match/" + g.id())
                .header("Authorization", "Bearer " + alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"hello team\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber());

        List<ChatMessage> rows = chatRepo.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getChannelType()).isEqualTo(ChannelType.MATCH);
        assertThat(rows.get(0).getChannelKey()).isEqualTo(g.id().toString());
        assertThat(rows.get(0).getSenderId()).isEqualTo(alice.id());
        assertThat(rows.get(0).getBody()).isEqualTo("hello team");
    }

    @Test
    void dmSendRequiresFriendship() throws Exception {
        Account alice = registerVerifyLogin("alice@example.com", "alice");
        Account bob = registerVerifyLogin("bob@example.com", "bob");
        // Strangers — no friendship

        mvc.perform(post("/api/chat/dm/" + bob.id())
                .header("Authorization", "Bearer " + alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"hi\"}"))
            .andExpect(status().is5xxServerError());

        assertThat(chatRepo.findAll()).isEmpty();
    }

    @Test
    void dmSendRejectedWhenBlocked() throws Exception {
        Account alice = registerVerifyLogin("alice@example.com", "alice");
        Account bob = registerVerifyLogin("bob@example.com", "bob");
        becomeFriends(alice.id(), bob.id());
        // bob blocks alice
        blocks.save(new UserBlock(bob.id(), alice.id()));

        mvc.perform(post("/api/chat/dm/" + bob.id())
                .header("Authorization", "Bearer " + alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"hi\"}"))
            .andExpect(status().is5xxServerError());

        assertThat(chatRepo.findAll()).isEmpty();
    }

    @Test
    void dmHistoryReturnsRecentOldestFirst() throws Exception {
        Account alice = registerVerifyLogin("alice@example.com", "alice");
        Account bob = registerVerifyLogin("bob@example.com", "bob");
        becomeFriends(alice.id(), bob.id());

        // Send 3 messages
        mvc.perform(post("/api/chat/dm/" + bob.id())
                .header("Authorization", "Bearer " + alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"first\"}"))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/chat/dm/" + alice.id())
                .header("Authorization", "Bearer " + bob.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"second\"}"))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/chat/dm/" + bob.id())
                .header("Authorization", "Bearer " + alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"third\"}"))
            .andExpect(status().isCreated());

        mvc.perform(get("/api/chat/dm/" + bob.id())
                .header("Authorization", "Bearer " + alice.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].body").value("first"))
            .andExpect(jsonPath("$[1].body").value("second"))
            .andExpect(jsonPath("$[2].body").value("third"));
    }

    @Test
    void rateLimitReturns429AfterEleventhMessage() throws Exception {
        Account alice = registerVerifyLogin("alice@example.com", "alice");
        Account bob = registerVerifyLogin("bob@example.com", "bob");
        becomeFriends(alice.id(), bob.id());

        // First 10 should succeed
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/chat/dm/" + bob.id())
                    .header("Authorization", "Bearer " + alice.jwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"msg " + i + "\"}"))
                .andExpect(status().isCreated());
        }
        // 11th must hit the limiter
        mvc.perform(post("/api/chat/dm/" + bob.id())
                .header("Authorization", "Bearer " + alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"overflow\"}"))
            .andExpect(status().is(429));
    }
}
