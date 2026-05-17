package com.remi.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remi.auth.domain.AuthTokens;
import com.remi.engine.domain.Action;
import com.remi.engine.domain.Difficulty;
import com.remi.engine.domain.Mode;
import com.remi.lobby.domain.LobbyGame;
import com.remi.lobby.service.LobbyService;
import com.remi.user.api.MockMailServiceTestConfig;
import com.remi.user.service.AuthService;
import com.remi.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Type;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(MockMailServiceTestConfig.class)
class GameWsControllerIntegrationTest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @LocalServerPort int port;
  @Autowired UserService userService;
  @Autowired AuthService authService;
  @Autowired LobbyService lobby;
  @Autowired ObjectMapper om;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach void reset() {
    MockMailServiceTestConfig.SENT.clear();
    jdbc.execute("TRUNCATE game_players, games, refresh_tokens, verification_tokens, password_reset_tokens, users CASCADE");
  }

  private AuthTokens registerLogin(String email, String username) {
    userService.register(email, username, "passwordxx");
    userService.verifyEmail(MockMailServiceTestConfig.SENT.get(MockMailServiceTestConfig.SENT.size() - 1).token());
    return authService.login(email, "passwordxx");
  }

  private StompSession connect(AuthTokens tokens) throws Exception {
    WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
    client.setMessageConverter(new MappingJackson2MessageConverter());
    StompHeaders headers = new StompHeaders();
    headers.add("Authorization", "Bearer " + tokens.accessToken());
    StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
      @Override public void handleException(StompSession s, org.springframework.messaging.simp.stomp.StompCommand cmd,
          StompHeaders h, byte[] payload, Throwable ex) {
        System.err.println("[STOMP] handleException cmd=" + cmd + " : " + ex);
        ex.printStackTrace();
      }
      @Override public void handleTransportError(StompSession s, Throwable ex) {
        System.err.println("[STOMP] handleTransportError : " + ex);
        ex.printStackTrace();
      }
      @Override public void handleFrame(StompHeaders h, Object payload) {
        System.err.println("[STOMP] handleFrame headers=" + h + " payload=" + payload);
      }
    };
    return client.connectAsync("ws://localhost:" + port + "/ws",
            (WebSocketHttpHeaders) null, headers, handler)
        .get(5, TimeUnit.SECONDS);
  }

  @Test
  void twoPlayersBroadcastShowsHiddenHands() throws Exception {
    AuthTokens aT = registerLogin("a@b.com", "alice");
    AuthTokens bT = registerLogin("b@c.com", "bob");
    UUID aId = userService.getByEmail("a@b.com").id();
    UUID bId = userService.getByEmail("b@c.com").id();

    LobbyGame g = lobby.createPrivate(aId, 2, Mode.ETALAT, Difficulty.MED);
    lobby.joinByCode(bId, g.joinCode());

    StompSession aSession = connect(aT);
    StompSession bSession = connect(bT);

    BlockingQueue<JsonNode> aMessages = new LinkedBlockingQueue<>();
    BlockingQueue<JsonNode> bMessages = new LinkedBlockingQueue<>();
    BlockingQueue<JsonNode> aErrors = new LinkedBlockingQueue<>();

    aSession.subscribe("/user/queue/games/" + g.id(), new StompFrameHandler() {
      @Override public Type getPayloadType(StompHeaders h) { return byte[].class; }
      @Override public void handleFrame(StompHeaders h, Object p) {
        try { aMessages.add(om.readTree((byte[]) p)); } catch (Exception ignored) {}
      }
    });
    bSession.subscribe("/user/queue/games/" + g.id(), new StompFrameHandler() {
      @Override public Type getPayloadType(StompHeaders h) { return byte[].class; }
      @Override public void handleFrame(StompHeaders h, Object p) {
        try { bMessages.add(om.readTree((byte[]) p)); } catch (Exception ignored) {}
      }
    });
    aSession.subscribe("/user/queue/errors", new StompFrameHandler() {
      @Override public Type getPayloadType(StompHeaders h) { return byte[].class; }
      @Override public void handleFrame(StompHeaders h, Object p) {
        try { aErrors.add(om.readTree((byte[]) p)); } catch (Exception ignored) {}
      }
    });

    // Give STOMP a moment to register subscriptions
    Thread.sleep(300);

    // Player A (seat 0) discards first piece — game starts in DISCARD phase for player 0
    String fullState = jdbc.queryForObject(
        "SELECT state::text FROM games WHERE id=?",
        String.class, g.id());
    JsonNode stateJson = om.readTree(fullState);
    int firstPieceId = stateJson.get("players").get(0).get("hand").get(0).get("id").asInt();

    java.util.Map<String, Object> discardMap = java.util.Map.of(
        "type", "DISCARD", "playerIdx", 0, "pieceId", firstPieceId);
    aSession.send("/app/games/" + g.id() + "/actions", discardMap);

    JsonNode aMsg = aMessages.poll(3, TimeUnit.SECONDS);
    JsonNode bMsg = bMessages.poll(3, TimeUnit.SECONDS);

    assertThat(aMsg).as("A should receive game view").isNotNull();
    assertThat(bMsg).as("B should receive game view").isNotNull();

    // Both should see updated turn (now player 1)
    assertThat(aMsg.get("view").get("current").asInt()).isEqualTo(1);
    assertThat(bMsg.get("view").get("current").asInt()).isEqualTo(1);

    // A sees own hand (14 cards after discard); B sees A's hand as empty with handCount=14
    assertThat(aMsg.get("view").get("players").get(0).get("hand").size()).isEqualTo(14);
    assertThat(bMsg.get("view").get("players").get(0).get("hand").size()).isEqualTo(0);
    assertThat(bMsg.get("view").get("players").get(0).get("handCount").asInt()).isEqualTo(14);

    // A (seat 0) claims to be at seat 1 → service rejects with NOT_YOUR_SEAT
    // (delivered to A's /user/queue/errors via @SendToUser)
    java.util.Map<String, Object> drawMap = java.util.Map.of(
        "type", "DRAW_FROM_STOCK", "playerIdx", 1);
    aSession.send("/app/games/" + g.id() + "/actions", drawMap);

    JsonNode err = aErrors.poll(3, TimeUnit.SECONDS);
    assertThat(err).as("A should receive NOT_YOUR_SEAT error").isNotNull();
    assertThat(err.get("code").asText()).isEqualTo("NOT_YOUR_SEAT");

    aSession.disconnect();
    bSession.disconnect();
  }
}
