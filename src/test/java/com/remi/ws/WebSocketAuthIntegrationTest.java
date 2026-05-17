package com.remi.ws;

import com.remi.auth.domain.AuthTokens;
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
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(MockMailServiceTestConfig.class)
class WebSocketAuthIntegrationTest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @LocalServerPort int port;

  @Autowired UserService userService;
  @Autowired AuthService authService;
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

  private WebSocketStompClient newClient() {
    WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
    client.setMessageConverter(new MappingJackson2MessageConverter());
    return client;
  }

  @Test
  void connectWithValidJwtSucceeds() throws Exception {
    AuthTokens tokens = registerLogin("a@b.com", "alice");
    WebSocketStompClient client = newClient();
    StompHeaders headers = new StompHeaders();
    headers.add("Authorization", "Bearer " + tokens.accessToken());
    StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", (WebSocketHttpHeaders) null, headers, new StompSessionHandlerAdapter() {})
        .get(5, TimeUnit.SECONDS);
    assertThat(session.isConnected()).isTrue();
    session.disconnect();
  }

  @Test
  void connectWithoutJwtFails() {
    WebSocketStompClient client = newClient();
    assertThatThrownBy(() ->
        client.connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS)
    ).isInstanceOf(ExecutionException.class);
  }

  @Test
  void connectWithInvalidJwtFails() {
    WebSocketStompClient client = newClient();
    StompHeaders headers = new StompHeaders();
    headers.add("Authorization", "Bearer not.a.real.token");
    assertThatThrownBy(() ->
        client.connectAsync("ws://localhost:" + port + "/ws", (WebSocketHttpHeaders) null, headers, new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS)
    ).isInstanceOf(ExecutionException.class);
  }
}
