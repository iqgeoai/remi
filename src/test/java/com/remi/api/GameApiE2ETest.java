package com.remi.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
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
class GameApiE2ETest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper om;

  @Test
  void createGameThenRunBotsThenClose() throws Exception {
    String body = """
        {"numPlayers":3,"mode":"ETALAT","difficulty":"MED","seed":42}
        """;
    String resp = mvc.perform(post("/api/dev/games").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    JsonNode view = om.readTree(resp);
    String id = view.get("id").asText();

    // Discard first piece (player 0 starts in DISCARD phase with 15 cards)
    int firstPieceId = view.get("players").get(0).get("hand").get(0).get("id").asInt();
    String discardBody = String.format("{\"type\":\"DISCARD\",\"playerIdx\":0,\"pieceId\":%d}", firstPieceId);
    mvc.perform(post("/api/dev/games/" + id + "/actions").contentType(MediaType.APPLICATION_JSON).content(discardBody))
        .andExpect(status().isOk());

    // Let bots play until they finish or it's player 0 again
    String afterBots = mvc.perform(post("/api/dev/games/" + id + "/bot"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    JsonNode afterBotsView = om.readTree(afterBots);
    // Either game closed, or it's player 0's turn
    boolean closed = afterBotsView.get("closed").asBoolean();
    int current = afterBotsView.get("current").asInt();
    assertThat(closed || current == 0).isTrue();
  }

  @Test
  void invalidActionReturns400() throws Exception {
    String body = """
        {"numPlayers":2,"mode":"ETALAT","difficulty":"MED","seed":42}
        """;
    String resp = mvc.perform(post("/api/dev/games").contentType(MediaType.APPLICATION_JSON).content(body))
        .andReturn().getResponse().getContentAsString();
    String id = om.readTree(resp).get("id").asText();

    String invalidAction = "{\"type\":\"DISCARD\",\"playerIdx\":0,\"pieceId\":999999}";
    mvc.perform(post("/api/dev/games/" + id + "/actions").contentType(MediaType.APPLICATION_JSON).content(invalidAction))
        .andExpect(status().isBadRequest());
  }
}
