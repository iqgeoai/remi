package com.remi.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remi.auth.domain.AuthTokens;
import com.remi.engine.domain.Difficulty;
import com.remi.engine.domain.Mode;
import com.remi.lobby.domain.MatchConfig;
import com.remi.lobby.service.MatchmakingService;
import com.remi.lobby.service.MatchmakingServiceImpl;
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
class MatchmakingNotificationIntegrationTest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @LocalServerPort int port;
  @Autowired UserService userService;
  @Autowired AuthService authService;
  @Autowired MatchmakingService matchService;
  @Autowired ObjectMapper om;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach void reset() {
    MockMailServiceTestConfig.SENT.clear();
    jdbc.execute("TRUNCATE game_players, games, refresh_tokens, verification_tokens, password_reset_tokens, users CASCADE");
    ((MatchmakingServiceImpl) matchService).clearAllForTest();
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

  private StompFrameHandler captureTo(BlockingQueue<JsonNode> queue) {
    return new StompFrameHandler() {
      @Override public Type getPayloadType(StompHeaders h) { return byte[].class; }
      @Override public void handleFrame(StompHeaders h, Object p) {
        try { queue.add(om.readTree((byte[]) p)); } catch (Exception ignored) {}
      }
    };
  }

  @Test
  void bothMatchedUsersReceiveNotification() throws Exception {
    AuthTokens aT = registerLogin("a@b.com", "alice");
    AuthTokens bT = registerLogin("b@c.com", "bob");
    UUID aId = userService.getByEmail("a@b.com").id();
    UUID bId = userService.getByEmail("b@c.com").id();

    StompSession aSession = connect(aT);
    StompSession bSession = connect(bT);

    BlockingQueue<JsonNode> aMatches = new LinkedBlockingQueue<>();
    BlockingQueue<JsonNode> bMatches = new LinkedBlockingQueue<>();

    aSession.subscribe("/user/queue/match", captureTo(aMatches));
    bSession.subscribe("/user/queue/match", captureTo(bMatches));

    // Give STOMP a moment to register subscriptions
    Thread.sleep(200);

    MatchConfig cfg = new MatchConfig(2, Mode.ETALAT, Difficulty.MED);
    matchService.enqueue(aId, cfg);
    matchService.enqueue(bId, cfg);

    JsonNode aMsg = aMatches.poll(3, TimeUnit.SECONDS);
    JsonNode bMsg = bMatches.poll(3, TimeUnit.SECONDS);

    assertThat(aMsg).as("A should receive match notification").isNotNull();
    assertThat(bMsg).as("B should receive match notification").isNotNull();

    String aGameId = aMsg.get("id").asText();
    String bGameId = bMsg.get("id").asText();
    assertThat(aGameId).isEqualTo(bGameId);

    aSession.disconnect();
    bSession.disconnect();
  }
}
