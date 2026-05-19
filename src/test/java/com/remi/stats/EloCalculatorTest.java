package com.remi.stats;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EloCalculatorTest {
    @Test
    void twoPlayerEqualRatingsWinnerGainsK() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        Map<UUID, Integer> r = Map.of(a, 1000, b, 1000);
        Map<UUID, Integer> ranks = Map.of(a, 1, b, 2);
        Map<UUID, Integer> result = EloCalculator.apply(r, ranks);
        assertEquals(1016, result.get(a));
        assertEquals(984, result.get(b));
    }

    @Test
    void twoPlayerHigherRatedLosesMoreOnUpset() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        Map<UUID, Integer> r = Map.of(a, 1500, b, 1000);
        Map<UUID, Integer> ranks = Map.of(a, 2, b, 1); // upset: lower-rated wins
        Map<UUID, Integer> result = EloCalculator.apply(r, ranks);
        // Lower-rated (b) should gain MORE than 16
        assertTrue(result.get(b) - 1000 > 16, "expected >16 gain for upset, got " + (result.get(b) - 1000));
    }

    @Test
    void fourPlayerWinnerGainsAcrossThreeOpponents() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        Map<UUID, Integer> r = Map.of(a, 1000, b, 1000, c, 1000, d, 1000);
        Map<UUID, Integer> ranks = Map.of(a, 1, b, 2, c, 3, d, 4);
        Map<UUID, Integer> result = EloCalculator.apply(r, ranks);
        // Winner: actual=3, expected=1.5, delta = (1.5)*32/3 = 16
        assertEquals(1016, result.get(a));
        // Last place: actual=0, expected=1.5, delta = -1.5*32/3 = -16
        assertEquals(984, result.get(d));
    }

    @Test
    void ratingFloorAtZero() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        Map<UUID, Integer> r = Map.of(a, 10, b, 3000);
        Map<UUID, Integer> ranks = Map.of(a, 2, b, 1); // expected
        Map<UUID, Integer> result = EloCalculator.apply(r, ranks);
        assertTrue(result.get(a) >= 0);
    }
}
