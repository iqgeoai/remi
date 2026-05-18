package com.remi.friends;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(MockMailServiceTestConfig.class)
class FriendsControllerTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;
    @Autowired FriendshipRepository friendships;

    @BeforeEach
    void reset() {
        MockMailServiceTestConfig.SENT.clear();
        jdbc.execute("TRUNCATE user_blocks, friendships, device_tokens, game_players, games, refresh_tokens, verification_tokens, password_reset_tokens, users CASCADE");
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
        // Look up user id from DB
        UUID id = jdbc.queryForObject(
            "SELECT id FROM users WHERE username_normalized = ?",
            UUID.class, username.toLowerCase());
        return new Account(id, jwt);
    }

    @Test
    void sendRequestCreatesPendingFriendship() throws Exception {
        Account alice = registerVerifyLogin("alice@example.com", "alice");
        Account bob = registerVerifyLogin("bob@example.com", "bob");

        String body = String.format("{\"addresseeId\":\"%s\"}", bob.id());
        mvc.perform(post("/api/friends/requests")
                .header("Authorization", "Bearer " + alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());

        assertThat(friendships.findAll()).singleElement().satisfies(f -> {
            assertThat(f.getRequesterId()).isEqualTo(alice.id());
            assertThat(f.getAddresseeId()).isEqualTo(bob.id());
            assertThat(f.getStatus()).isEqualTo(FriendshipStatus.PENDING);
        });
    }

    @Test
    void cannotSendRequestToSelf() throws Exception {
        Account alice = registerVerifyLogin("alice@example.com", "alice");

        String body = String.format("{\"addresseeId\":\"%s\"}", alice.id());
        // Service throws IllegalArgumentException; no specific handler → 500.
        // Important: the friendship row must NOT be created.
        mvc.perform(post("/api/friends/requests")
                .header("Authorization", "Bearer " + alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().is5xxServerError());

        assertThat(friendships.findAll()).isEmpty();
    }

    @Test
    void acceptChangesStatusToAccepted() throws Exception {
        Account alice = registerVerifyLogin("alice@example.com", "alice");
        Account bob = registerVerifyLogin("bob@example.com", "bob");

        String sendBody = String.format("{\"addresseeId\":\"%s\"}", bob.id());
        String sendResp = mvc.perform(post("/api/friends/requests")
                .header("Authorization", "Bearer " + alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(sendBody))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long requestId = om.readTree(sendResp).get("id").asLong();

        mvc.perform(post("/api/friends/requests/" + requestId + "/accept")
                .header("Authorization", "Bearer " + bob.jwt()))
            .andExpect(status().isNoContent());

        assertThat(friendships.findById(requestId)).get().satisfies(f ->
            assertThat(f.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED));
    }

    @Test
    void onlyAddresseeCanAccept() throws Exception {
        Account alice = registerVerifyLogin("alice@example.com", "alice");
        Account bob = registerVerifyLogin("bob@example.com", "bob");
        Account carol = registerVerifyLogin("carol@example.com", "carol");

        String sendBody = String.format("{\"addresseeId\":\"%s\"}", bob.id());
        String sendResp = mvc.perform(post("/api/friends/requests")
                .header("Authorization", "Bearer " + alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(sendBody))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long requestId = om.readTree(sendResp).get("id").asLong();

        // Carol (not the addressee) tries to accept → server error from SecurityException
        mvc.perform(post("/api/friends/requests/" + requestId + "/accept")
                .header("Authorization", "Bearer " + carol.jwt()))
            .andExpect(status().is5xxServerError());

        assertThat(friendships.findById(requestId)).get().satisfies(f ->
            assertThat(f.getStatus()).isEqualTo(FriendshipStatus.PENDING));
    }

    @Test
    void listFriendsReturnsAcceptedWithOnlineFalse() throws Exception {
        Account alice = registerVerifyLogin("alice@example.com", "alice");
        Account bob = registerVerifyLogin("bob@example.com", "bob");

        String sendBody = String.format("{\"addresseeId\":\"%s\"}", bob.id());
        String sendResp = mvc.perform(post("/api/friends/requests")
                .header("Authorization", "Bearer " + alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(sendBody))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long requestId = om.readTree(sendResp).get("id").asLong();
        mvc.perform(post("/api/friends/requests/" + requestId + "/accept")
                .header("Authorization", "Bearer " + bob.jwt()))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/friends")
                .header("Authorization", "Bearer " + alice.jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(bob.id().toString()))
            .andExpect(jsonPath("$[0].username").value("bob"))
            .andExpect(jsonPath("$[0].online").value(false));
    }

    @Test
    void inviteCreatesPrivateMatchForAcceptedFriend() throws Exception {
        Account alice = registerVerifyLogin("alice@example.com", "alice");
        Account bob = registerVerifyLogin("bob@example.com", "bob");

        // alice → bob friendship accepted
        String sendBody = String.format("{\"addresseeId\":\"%s\"}", bob.id());
        String sendResp = mvc.perform(post("/api/friends/requests")
                .header("Authorization", "Bearer " + alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(sendBody))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long requestId = om.readTree(sendResp).get("id").asLong();
        mvc.perform(post("/api/friends/requests/" + requestId + "/accept")
                .header("Authorization", "Bearer " + bob.jwt()))
            .andExpect(status().isNoContent());

        // alice invites bob with default settings (empty body)
        String inviteResp = mvc.perform(post("/api/friends/" + bob.id() + "/invite")
                .header("Authorization", "Bearer " + alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").isString())
            .andExpect(jsonPath("$.matchId").isString())
            .andReturn().getResponse().getContentAsString();

        String code = om.readTree(inviteResp).get("code").asText();
        UUID matchId = UUID.fromString(om.readTree(inviteResp).get("matchId").asText());
        // Verify a private game row exists with that join code, owned by alice
        UUID dbOwner = jdbc.queryForObject(
            "SELECT owner_id FROM games WHERE id = ?", UUID.class, matchId);
        String dbCode = jdbc.queryForObject(
            "SELECT join_code FROM games WHERE id = ?", String.class, matchId);
        assertThat(dbOwner).isEqualTo(alice.id());
        assertThat(dbCode).isEqualTo(code);
    }

    @Test
    void inviteRejectedWhenNotAcceptedFriend() throws Exception {
        Account alice = registerVerifyLogin("alice@example.com", "alice");
        Account bob = registerVerifyLogin("bob@example.com", "bob");

        // No friendship at all → IllegalStateException → 5xx
        mvc.perform(post("/api/friends/" + bob.id() + "/invite")
                .header("Authorization", "Bearer " + alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().is5xxServerError());
    }

    @Test
    void unfriendRemovesRow() throws Exception {
        Account alice = registerVerifyLogin("alice@example.com", "alice");
        Account bob = registerVerifyLogin("bob@example.com", "bob");

        String sendBody = String.format("{\"addresseeId\":\"%s\"}", bob.id());
        String sendResp = mvc.perform(post("/api/friends/requests")
                .header("Authorization", "Bearer " + alice.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(sendBody))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long requestId = om.readTree(sendResp).get("id").asLong();
        mvc.perform(post("/api/friends/requests/" + requestId + "/accept")
                .header("Authorization", "Bearer " + bob.jwt()))
            .andExpect(status().isNoContent());
        assertThat(friendships.findAll()).hasSize(1);

        mvc.perform(delete("/api/friends/" + bob.id())
                .header("Authorization", "Bearer " + alice.jwt()))
            .andExpect(status().isNoContent());

        assertThat(friendships.findAll()).isEmpty();
    }
}
