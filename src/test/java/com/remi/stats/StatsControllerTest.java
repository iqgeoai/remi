package com.remi.stats;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(MockMailServiceTestConfig.class)
class StatsControllerTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;
    @Autowired MatchHistoryRepository historyRepo;
    @Autowired MatchHistoryScoreRepository scoreRepo;

    @BeforeEach
    void reset() {
        MockMailServiceTestConfig.SENT.clear();
        jdbc.execute("TRUNCATE match_history_score, match_history, chat_messages, user_blocks, friendships, device_tokens, game_players, games, refresh_tokens, verification_tokens, password_reset_tokens, users CASCADE");
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

    @Test
    void profileForFreshUserReturnsDefaultRatingAndZeroMatches() throws Exception {
        Account alice = registerVerifyLogin("alice@example.com", "alice");

        mvc.perform(get("/api/users/" + alice.id() + "/profile")
                .header("Authorization", "Bearer " + alice.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(alice.id().toString()))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.rating").value(1000))
            .andExpect(jsonPath("$.totalMatches").value(0))
            .andExpect(jsonPath("$.wins").value(0))
            .andExpect(jsonPath("$.losses").value(0))
            .andExpect(jsonPath("$.winRate").value(0.0))
            .andExpect(jsonPath("$.totalPoints").value(0))
            .andExpect(jsonPath("$.recentMatches.length()").value(0));
    }

    @Test
    void profileReflectsSavedMatchHistoryAndScoreRows() throws Exception {
        Account alice = registerVerifyLogin("alice@example.com", "alice");
        Account bob = registerVerifyLogin("bob@example.com", "bob");

        // Seed a fake game row so the FK on match_history.game_id is satisfied.
        UUID gameId = UUID.randomUUID();
        jdbc.update("INSERT INTO games (id, state, version, visibility) VALUES (?, '{}'::jsonb, 0, 'PRIVATE')", gameId);

        // Persist a history + 2 score rows directly via the repos.
        MatchHistory h = historyRepo.save(new MatchHistory(gameId, 120, alice.id()));
        scoreRepo.save(new MatchHistoryScore(h.getId(), alice.id(), 30, 1, 1000, 1016));
        scoreRepo.save(new MatchHistoryScore(h.getId(), bob.id(), 80, 2, 1000, 984));

        // Also bump the user rating row to match (real flow does this in MatchHistoryService).
        jdbc.update("UPDATE users SET rating = ? WHERE id = ?", 1016, alice.id());
        jdbc.update("UPDATE users SET rating = ? WHERE id = ?", 984, bob.id());

        mvc.perform(get("/api/users/" + alice.id() + "/profile")
                .header("Authorization", "Bearer " + alice.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rating").value(1016))
            .andExpect(jsonPath("$.totalMatches").value(1))
            .andExpect(jsonPath("$.wins").value(1))
            .andExpect(jsonPath("$.losses").value(0))
            .andExpect(jsonPath("$.totalPoints").value(30))
            .andExpect(jsonPath("$.recentMatches.length()").value(1))
            .andExpect(jsonPath("$.recentMatches[0].rank").value(1))
            .andExpect(jsonPath("$.recentMatches[0].ratingDelta").value(16))
            .andExpect(jsonPath("$.recentMatches[0].winnerUsername").value("alice"));

        // Bob is the loser
        mvc.perform(get("/api/users/" + bob.id() + "/profile")
                .header("Authorization", "Bearer " + bob.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rating").value(984))
            .andExpect(jsonPath("$.totalMatches").value(1))
            .andExpect(jsonPath("$.wins").value(0))
            .andExpect(jsonPath("$.losses").value(1))
            .andExpect(jsonPath("$.recentMatches[0].rank").value(2))
            .andExpect(jsonPath("$.recentMatches[0].ratingDelta").value(-16));
    }

    @Test
    void leaderboardOrdersByRatingDescending() throws Exception {
        Account alice = registerVerifyLogin("alice@example.com", "alice");
        Account bob = registerVerifyLogin("bob@example.com", "bob");
        Account carol = registerVerifyLogin("carol@example.com", "carol");

        jdbc.update("UPDATE users SET rating = ? WHERE id = ?", 1200, alice.id());
        jdbc.update("UPDATE users SET rating = ? WHERE id = ?", 900, bob.id());
        jdbc.update("UPDATE users SET rating = ? WHERE id = ?", 1100, carol.id());

        mvc.perform(get("/api/leaderboard")
                .header("Authorization", "Bearer " + alice.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].username").value("alice"))
            .andExpect(jsonPath("$[0].rating").value(1200))
            .andExpect(jsonPath("$[1].username").value("carol"))
            .andExpect(jsonPath("$[1].rating").value(1100))
            .andExpect(jsonPath("$[2].username").value("bob"))
            .andExpect(jsonPath("$[2].rating").value(900));
    }

    @Test
    void myStatsReturnsCurrentUserProfile() throws Exception {
        Account alice = registerVerifyLogin("alice@example.com", "alice");

        mvc.perform(get("/api/users/me/stats")
                .header("Authorization", "Bearer " + alice.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(alice.id().toString()))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.rating").value(1000));
    }
}
