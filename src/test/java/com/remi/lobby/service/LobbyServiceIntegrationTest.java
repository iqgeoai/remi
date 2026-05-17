package com.remi.lobby.service;

import com.remi.engine.domain.Difficulty;
import com.remi.engine.domain.Mode;
import com.remi.lobby.domain.GameVisibility;
import com.remi.lobby.domain.LobbyGame;
import com.remi.lobby.persistence.GamePlayerRepository;
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
class LobbyServiceIntegrationTest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired LobbyService lobbyService;
  @Autowired UserService userService;
  @Autowired GamePlayerRepository playersRepo;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void reset() {
    MockMailServiceTestConfig.SENT.clear();
    jdbc.execute("TRUNCATE game_players, games, refresh_tokens, verification_tokens, password_reset_tokens, users CASCADE");
  }

  private UUID registerVerified(String email, String username) {
    var u = userService.register(email, username, "passwordxx");
    userService.verifyEmail(MockMailServiceTestConfig.SENT.get(MockMailServiceTestConfig.SENT.size() - 1).token());
    return u.id();
  }

  @Test
  void createPrivateInsertsGameWithCodeAndSeatsOwner() {
    UUID owner = registerVerified("a@b.com", "alice");
    LobbyGame g = lobbyService.createPrivate(owner, 3, Mode.ETALAT, Difficulty.MED);
    assertThat(g.ownerId()).isEqualTo(owner);
    assertThat(g.visibility()).isEqualTo(GameVisibility.PRIVATE);
    assertThat(g.joinCode()).isNotNull().hasSize(8);
    assertThat(g.seatsTaken()).isEqualTo(1);
    assertThat(g.started()).isFalse();
    assertThat(playersRepo.findSeat(g.id(), owner)).hasValue(0);
  }

  @Test
  void createPublicHasNoJoinCode() {
    UUID owner = registerVerified("a@b.com", "alice");
    LobbyGame g = lobbyService.createPublic(owner, 2, Mode.ETALAT, Difficulty.MED);
    assertThat(g.visibility()).isEqualTo(GameVisibility.PUBLIC);
    assertThat(g.joinCode()).isNull();
  }

  @Test
  void joinByCodeAddsPlayer() {
    UUID owner = registerVerified("a@b.com", "alice");
    UUID guest = registerVerified("b@c.com", "bob");
    LobbyGame g = lobbyService.createPrivate(owner, 3, Mode.ETALAT, Difficulty.MED);
    LobbyGame after = lobbyService.joinByCode(guest, g.joinCode());
    assertThat(after.seatsTaken()).isEqualTo(2);
    assertThat(after.started()).isFalse();
    assertThat(playersRepo.findSeat(g.id(), guest)).hasValue(1);
  }

  @Test
  void joinByCodeRejectsUnknownCode() {
    UUID guest = registerVerified("b@c.com", "bob");
    assertThatThrownBy(() -> lobbyService.joinByCode(guest, "BAD12345"))
        .isInstanceOf(JoinCodeNotFoundException.class);
  }

  @Test
  void joinByCodeRejectsAlreadySeated() {
    UUID owner = registerVerified("a@b.com", "alice");
    LobbyGame g = lobbyService.createPrivate(owner, 3, Mode.ETALAT, Difficulty.MED);
    assertThatThrownBy(() -> lobbyService.joinByCode(owner, g.joinCode()))
        .isInstanceOf(AlreadySeatedException.class);
  }

  @Test
  void joinByCodeRejectsFullLobby() {
    UUID a = registerVerified("a@b.com", "alice");
    UUID b = registerVerified("b@c.com", "bob");
    UUID c = registerVerified("c@d.com", "carol");
    LobbyGame g = lobbyService.createPrivate(a, 2, Mode.ETALAT, Difficulty.MED);
    lobbyService.joinByCode(b, g.joinCode());  // fills lobby; should auto-start
    assertThatThrownBy(() -> lobbyService.joinByCode(c, g.joinCode()))
        .isInstanceOf(LobbyFullException.class);
  }

  @Test
  void lastJoinerStartsGame() {
    UUID a = registerVerified("a@b.com", "alice");
    UUID b = registerVerified("b@c.com", "bob");
    LobbyGame g = lobbyService.createPrivate(a, 2, Mode.ETALAT, Difficulty.MED);
    LobbyGame after = lobbyService.joinByCode(b, g.joinCode());
    assertThat(after.started()).isTrue();
  }

  @Test
  void listPublicWaitingShowsOnlyPublicNotFull() {
    UUID a = registerVerified("a@b.com", "alice");
    UUID b = registerVerified("b@c.com", "bob");
    lobbyService.createPrivate(a, 3, Mode.ETALAT, Difficulty.MED);   // private excluded
    LobbyGame pub = lobbyService.createPublic(b, 3, Mode.ETALAT, Difficulty.MED);
    var list = lobbyService.listPublicWaiting();
    assertThat(list).extracting(LobbyGame::id).containsExactly(pub.id());
  }

  @Test
  void myGamesListsAllSeatedGames() {
    UUID alice = registerVerified("a@b.com", "alice");
    UUID bob = registerVerified("b@c.com", "bob");
    LobbyGame g1 = lobbyService.createPrivate(alice, 3, Mode.ETALAT, Difficulty.MED);
    LobbyGame g2 = lobbyService.createPrivate(bob, 3, Mode.ETALAT, Difficulty.MED);
    lobbyService.joinByCode(alice, g2.joinCode());
    var mine = lobbyService.myGames(alice);
    assertThat(mine).extracting(LobbyGame::id).containsExactlyInAnyOrder(g1.id(), g2.id());
  }

  @Test
  void leaveBeforeStartedSucceeds() {
    UUID alice = registerVerified("a@b.com", "alice");
    UUID bob = registerVerified("b@c.com", "bob");
    LobbyGame g = lobbyService.createPrivate(alice, 3, Mode.ETALAT, Difficulty.MED);
    lobbyService.joinByCode(bob, g.joinCode());
    lobbyService.leave(bob, g.id());
    assertThat(lobbyService.get(g.id()).seatsTaken()).isEqualTo(1);
  }

  @Test
  void leaveAfterStartedRejected() {
    UUID alice = registerVerified("a@b.com", "alice");
    UUID bob = registerVerified("b@c.com", "bob");
    LobbyGame g = lobbyService.createPrivate(alice, 2, Mode.ETALAT, Difficulty.MED);
    lobbyService.joinByCode(bob, g.joinCode());  // started
    assertThatThrownBy(() -> lobbyService.leave(alice, g.id()))
        .isInstanceOf(GameAlreadyStartedException.class);
  }
}
