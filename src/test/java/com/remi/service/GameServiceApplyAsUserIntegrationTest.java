package com.remi.service;

import com.remi.engine.domain.*;
import com.remi.lobby.service.LobbyService;
import com.remi.lobby.service.NotSeatedException;
import com.remi.lobby.service.NotYourSeatException;
import com.remi.user.api.MockMailServiceTestConfig;
import com.remi.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(MockMailServiceTestConfig.class)
class GameServiceApplyAsUserIntegrationTest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired GameService gameService;
  @Autowired LobbyService lobby;
  @Autowired UserService userService;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach void reset() {
    MockMailServiceTestConfig.SENT.clear();
    jdbc.execute("TRUNCATE game_players, games, refresh_tokens, verification_tokens, password_reset_tokens, users CASCADE");
  }

  private UUID registerVerified(String email, String username) {
    var u = userService.register(email, username, "passwordxx");
    userService.verifyEmail(MockMailServiceTestConfig.SENT.get(MockMailServiceTestConfig.SENT.size() - 1).token());
    return u.id();
  }

  @Test
  void seatedUserWithCorrectPlayerIdxAccepted() {
    UUID a = registerVerified("a@b.com", "alice");
    UUID b = registerVerified("b@c.com", "bob");
    var g = lobby.createPrivate(a, 2, Mode.ETALAT, Difficulty.MED);
    lobby.joinByCode(b, g.joinCode());
    GameState state = gameService.get(g.id());
    int firstPiece = state.players().get(0).hand().get(0).id();
    GameState after = gameService.applyActionAsUser(g.id(), a, new Action.Discard(0, firstPiece));
    assertThat(after.players().get(0).hand()).hasSize(14);
  }

  @Test
  void seatedUserWrongPlayerIdxRejected() {
    UUID a = registerVerified("a@b.com", "alice");
    UUID b = registerVerified("b@c.com", "bob");
    var g = lobby.createPrivate(a, 2, Mode.ETALAT, Difficulty.MED);
    lobby.joinByCode(b, g.joinCode());
    assertThatThrownBy(() -> gameService.applyActionAsUser(g.id(), a, new Action.Discard(1, 0)))
        .isInstanceOf(NotYourSeatException.class);
  }

  @Test
  void nonSeatedUserRejected() {
    UUID a = registerVerified("a@b.com", "alice");
    UUID b = registerVerified("b@c.com", "bob");
    UUID c = registerVerified("c@d.com", "carol");
    var g = lobby.createPrivate(a, 2, Mode.ETALAT, Difficulty.MED);
    lobby.joinByCode(b, g.joinCode());
    assertThatThrownBy(() -> gameService.applyActionAsUser(g.id(), c, new Action.Discard(0, 0)))
        .isInstanceOf(NotSeatedException.class);
  }
}
