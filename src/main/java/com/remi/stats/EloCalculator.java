package com.remi.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EloCalculator {
    private static final int K = 32;
    private EloCalculator() {}

    /** Compute new ratings given current ratings and the final ranks (1 = winner). */
    public static Map<UUID, Integer> apply(Map<UUID, Integer> currentRatings, Map<UUID, Integer> ranks) {
        int n = currentRatings.size();
        Map<UUID, Integer> newRatings = new HashMap<>();
        for (UUID a : currentRatings.keySet()) {
            double expected = 0.0;
            double actual = 0.0;
            int rA = currentRatings.get(a);
            for (UUID b : currentRatings.keySet()) {
                if (a.equals(b)) continue;
                int rB = currentRatings.get(b);
                double e = 1.0 / (1.0 + Math.pow(10.0, (rB - rA) / 400.0));
                expected += e;
                // actual: 1 if a outranks b (lower rank number = better), 0.5 if tie
                int ra = ranks.get(a), rb = ranks.get(b);
                if (ra < rb) actual += 1.0;
                else if (ra == rb) actual += 0.5;
            }
            // Normalize: distributing K across all opponents
            double delta = (actual - expected) * K / (n - 1);
            int newRating = (int) Math.round(rA + delta);
            newRatings.put(a, Math.max(0, newRating));
        }
        return newRatings;
    }
}
