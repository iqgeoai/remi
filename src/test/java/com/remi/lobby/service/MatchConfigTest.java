package com.remi.lobby.service;

import com.remi.engine.domain.Difficulty;
import com.remi.engine.domain.Mode;
import com.remi.lobby.domain.MatchConfig;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class MatchConfigTest {
  @Test void equalsByValue() {
    assertThat(new MatchConfig(2, Mode.ETALAT, Difficulty.MED))
        .isEqualTo(new MatchConfig(2, Mode.ETALAT, Difficulty.MED));
  }
  @Test void differsByNumPlayers() {
    assertThat(new MatchConfig(2, Mode.ETALAT, Difficulty.MED))
        .isNotEqualTo(new MatchConfig(3, Mode.ETALAT, Difficulty.MED));
  }
  @Test void differsByMode() {
    assertThat(new MatchConfig(2, Mode.ETALAT, Difficulty.MED))
        .isNotEqualTo(new MatchConfig(2, Mode.TABLA, Difficulty.MED));
  }
  @Test void usableAsMapKey() {
    Map<MatchConfig, String> map = new HashMap<>();
    MatchConfig key1 = new MatchConfig(2, Mode.ETALAT, Difficulty.MED);
    MatchConfig key2 = new MatchConfig(2, Mode.ETALAT, Difficulty.MED);
    map.put(key1, "value");
    assertThat(map.get(key2)).isEqualTo("value");
  }
}
