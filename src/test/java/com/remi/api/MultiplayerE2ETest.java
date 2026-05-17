package com.remi.api;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(MockMailServiceTestConfig.class)
class MultiplayerE2ETest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper om;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void reset() {
    MockMailServiceTestConfig.SENT.clear();
    jdbc.execute("TRUNCATE game_players, games, refresh_tokens, verification_tokens, password_reset_tokens, users CASCADE");
  }

  private String registerVerifyLogin(String email, String username) throws Exception {
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
    return om.readTree(loginResp).get("accessToken").asText();
  }

  @Test
  void fullMultiplayerHappyPath_register_login_create_join_play() throws Exception {
    // 1) Two users register/verify/login
    String aToken = registerVerifyLogin("alice@example.com", "alice");
    String bToken = registerVerifyLogin("bob@example.com", "bob");

    // 2) Alice creates a private 2-player game
    String createBody = """
        {"visibility":"PRIVATE","numPlayers":2,"mode":"ETALAT","difficulty":"MED"}""";
    String createResp = mvc.perform(post("/api/games")
            .header("Authorization", "Bearer " + aToken)
            .contentType(MediaType.APPLICATION_JSON).content(createBody))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    JsonNode created = om.readTree(createResp);
    String gameId = created.get("id").asText();
    String joinCode = created.get("joinCode").asText();
    assertThat(joinCode).isNotBlank().hasSize(8);
    assertThat(created.get("started").asBoolean()).isFalse();

    // 3) Bob joins by code → game auto-starts (lobby full)
    String joinBody = String.format("{\"joinCode\":\"%s\"}", joinCode);
    String joinResp = mvc.perform(post("/api/games/join-by-code")
            .header("Authorization", "Bearer " + bToken)
            .contentType(MediaType.APPLICATION_JSON).content(joinBody))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    JsonNode joined = om.readTree(joinResp);
    assertThat(joined.get("started").asBoolean()).isTrue();
    assertThat(joined.get("seatsTaken").asInt()).isEqualTo(2);

    // 4) Alice GETs the game state — her own hand is visible
    String viewResp = mvc.perform(get("/api/games/" + gameId)
            .header("Authorization", "Bearer " + aToken))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    JsonNode view = om.readTree(viewResp);
    assertThat(view.get("current").asInt()).isEqualTo(0);
    JsonNode aliceHand = view.get("players").get(0).get("hand");
    JsonNode bobHandFromAlice = view.get("players").get(1).get("hand");
    assertThat(aliceHand.size()).isGreaterThan(0); // Alice can see her own hand
    assertThat(bobHandFromAlice.size()).isZero();  // Bob's hand hidden from Alice
    int firstPieceId = aliceHand.get(0).get("id").asInt();

    // 5) Alice discards her first piece → view updates, current becomes 1
    String discardBody = String.format(
        "{\"action\":{\"type\":\"DISCARD\",\"playerIdx\":0,\"pieceId\":%d}}", firstPieceId);
    String discardResp = mvc.perform(post("/api/games/" + gameId + "/actions")
            .header("Authorization", "Bearer " + aToken)
            .contentType(MediaType.APPLICATION_JSON).content(discardBody))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    JsonNode afterDiscard = om.readTree(discardResp);
    assertThat(afterDiscard.get("current").asInt()).isEqualTo(1);

    // 6) Bob tries to act with the WRONG seat (playerIdx=0) → 403 NOT_YOUR_SEAT
    String wrongSeatBody = "{\"action\":{\"type\":\"DRAW_FROM_STOCK\",\"playerIdx\":0}}";
    mvc.perform(post("/api/games/" + gameId + "/actions")
            .header("Authorization", "Bearer " + bToken)
            .contentType(MediaType.APPLICATION_JSON).content(wrongSeatBody))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_YOUR_SEAT"));
  }
}
