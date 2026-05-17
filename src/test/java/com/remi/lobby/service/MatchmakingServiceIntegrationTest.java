package com.remi.lobby.service;

import com.remi.engine.domain.Difficulty;
import com.remi.engine.domain.Mode;
import com.remi.lobby.domain.LobbyGame;
import com.remi.lobby.domain.MatchConfig;
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
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(MockMailServiceTestConfig.class)
class MatchmakingServiceIntegrationTest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired MatchmakingService matchService;
  @Autowired UserService userService;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach void reset() {
    MockMailServiceTestConfig.SENT.clear();
    jdbc.execute("TRUNCATE game_players, games, refresh_tokens, verification_tokens, password_reset_tokens, users CASCADE");
    ((MatchmakingServiceImpl) matchService).clearAllForTest();
  }

  private UUID registerVerified(String email, String username) {
    var u = userService.register(email, username, "passwordxx");
    userService.verifyEmail(MockMailServiceTestConfig.SENT.get(MockMailServiceTestConfig.SENT.size() - 1).token());
    return u.id();
  }

  private MatchConfig cfg(int n) { return new MatchConfig(n, Mode.ETALAT, Difficulty.MED); }

  @Test
  void singleEnqueueQueues() {
    UUID a = registerVerified("a@b.com", "alice");
    Optional<LobbyGame> result = matchService.enqueue(a, cfg(2));
    assertThat(result).isEmpty();
    assertThat(matchService.queueDepth(cfg(2))).isEqualTo(1);
  }

  @Test
  void twoEnqueueMatches() {
    UUID a = registerVerified("a@b.com", "alice");
    UUID b = registerVerified("b@c.com", "bob");
    matchService.enqueue(a, cfg(2));
    Optional<LobbyGame> result = matchService.enqueue(b, cfg(2));
    assertThat(result).isPresent();
    assertThat(result.get().seatsTaken()).isEqualTo(2);
    assertThat(result.get().started()).isTrue();
    assertThat(matchService.queueDepth(cfg(2))).isZero();
  }

  @Test
  void differentConfigsKeepSeparateQueues() {
    UUID a = registerVerified("a@b.com", "alice");
    UUID b = registerVerified("b@c.com", "bob");
    matchService.enqueue(a, cfg(2));
    matchService.enqueue(b, cfg(3));
    assertThat(matchService.queueDepth(cfg(2))).isEqualTo(1);
    assertThat(matchService.queueDepth(cfg(3))).isEqualTo(1);
  }

  @Test
  void cancelRemovesFromQueue() {
    UUID a = registerVerified("a@b.com", "alice");
    matchService.enqueue(a, cfg(2));
    matchService.cancel(a);
    assertThat(matchService.queueDepth(cfg(2))).isZero();
  }

  @Test
  void doubleEnqueueRejected() {
    UUID a = registerVerified("a@b.com", "alice");
    matchService.enqueue(a, cfg(2));
    assertThatThrownBy(() -> matchService.enqueue(a, cfg(2)))
        .isInstanceOf(MatchmakingAlreadyQueuedException.class);
  }

  @Test
  void concurrentEnqueueMakesCleanMatches() throws Exception {
    int total = 4;
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < total; i++) ids.add(registerVerified("u" + i + "@e.com", "usr" + i));

    ExecutorService ex = Executors.newFixedThreadPool(total);
    List<Future<Optional<LobbyGame>>> futures = new ArrayList<>();
    CountDownLatch start = new CountDownLatch(1);
    for (UUID id : ids) {
      futures.add(ex.submit(() -> {
        start.await();
        return matchService.enqueue(id, cfg(2));
      }));
    }
    start.countDown();
    int matches = 0;
    for (Future<Optional<LobbyGame>> f : futures) {
      if (f.get(5, TimeUnit.SECONDS).isPresent()) matches++;
    }
    ex.shutdown();
    // Exactly 2 calls (the second of each pair) should return a match.
    assertThat(matches).isEqualTo(2);
    assertThat(matchService.queueDepth(cfg(2))).isZero();
  }
}
