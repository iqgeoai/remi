package com.remi.persistence;

import com.remi.engine.domain.*;
import com.remi.engine.rules.Dealer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.remi.config.JacksonConfig;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JacksonConfig.class)
@ActiveProfiles("test")
class GameRepositoryIT {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired GameRepository repo;

  @Test
  void roundTripsGameStateThroughJsonb() {
    GameState s = Dealer.deal(3, Mode.ETALAT, Difficulty.MED, 42L);
    GameEntity e = new GameEntity(s.id(), s);
    repo.save(e);
    GameEntity loaded = repo.findById(s.id()).orElseThrow();
    assertThat(loaded.getState()).isEqualTo(s);
  }
}
