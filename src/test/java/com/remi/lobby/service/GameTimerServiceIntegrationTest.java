package com.remi.lobby.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.remi.user.api.MockMailServiceTestConfig;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(MockMailServiceTestConfig.class)
class GameTimerServiceIntegrationTest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired GameTimerService timer;

  @Test
  void taskFiresAtConfiguredTtl() throws Exception {
    UUID gameId = UUID.randomUUID();
    CountDownLatch latch = new CountDownLatch(1);
    timer.scheduleHardTimeout(gameId, 0, latch::countDown);
    // test profile sets hard-timeout=PT2S
    assertThat(latch.await(4, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void cancelBeforeFirePreventsExecution() throws Exception {
    UUID gameId = UUID.randomUUID();
    CountDownLatch latch = new CountDownLatch(1);
    timer.scheduleHardTimeout(gameId, 0, latch::countDown);
    timer.cancel(gameId);
    assertThat(latch.await(3, TimeUnit.SECONDS)).isFalse();
  }

  @Test
  void cancelOnUnknownGameIsNoOp() {
    assertThatNoException().isThrownBy(() -> timer.cancel(UUID.randomUUID()));
  }

  @Test
  void rescheduleCancelsPriorTask() throws Exception {
    UUID gameId = UUID.randomUUID();
    CountDownLatch first = new CountDownLatch(1);
    CountDownLatch second = new CountDownLatch(1);
    timer.scheduleHardTimeout(gameId, 0, first::countDown);
    timer.scheduleHardTimeout(gameId, 0, second::countDown);
    assertThat(second.await(4, TimeUnit.SECONDS)).isTrue();
    assertThat(first.getCount()).isEqualTo(1);   // first never fired
  }
}
