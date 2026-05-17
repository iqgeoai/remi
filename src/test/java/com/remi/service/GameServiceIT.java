package com.remi.service;

import com.remi.engine.domain.*;
import com.remi.persistence.GameRepository;
import com.remi.user.api.MockMailServiceTestConfig;
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
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(MockMailServiceTestConfig.class)
class GameServiceIT {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired GameService service;
  @Autowired GameRepository repo;

  @Test
  void createReturnsValidGame() {
    GameState s = service.create(3, Mode.ETALAT, Difficulty.MED, 42L);
    assertThat(s.players()).hasSize(3);
    assertThat(repo.findById(s.id())).isPresent();
  }

  @Test
  void applyActionPersistsNewState() {
    GameState s = service.create(2, Mode.ETALAT, Difficulty.MED, 42L);
    // Initial state: phase=DISCARD for player 0 (with 15 cards). Discard the first card.
    int firstPieceId = s.players().get(0).hand().get(0).id();
    GameState after = service.applyAction(s.id(), new Action.Discard(0, firstPieceId));
    assertThat(after.players().get(0).hand()).hasSize(14);
  }

  @Test
  void applyActionWithRuleViolationThrowsAndDoesNotMutate() {
    GameState s = service.create(2, Mode.ETALAT, Difficulty.MED, 42L);
    assertThatThrownBy(() -> service.applyAction(s.id(), new Action.Discard(0, 99999)))
        .isInstanceOf(GameRuleException.class);
    GameState reloaded = service.get(s.id());
    assertThat(reloaded.players().get(0).hand()).hasSize(15);  // unchanged
  }

  @Test
  void getThrowsOnMissing() {
    assertThatThrownBy(() -> service.get(UUID.randomUUID()))
        .isInstanceOf(GameNotFoundException.class);
  }
}
