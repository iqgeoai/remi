package com.remi.engine.rules;

import com.remi.engine.domain.Meld;
import com.remi.engine.domain.Piece;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MeldValidator {
  private MeldValidator() {}

  public static boolean isValid(Meld meld) {
    if (meld.pieces() == null || meld.pieces().size() < 3) return false;
    List<Piece> pieces = meld.pieces();
    long jokerCount = pieces.stream().filter(Piece::isJoker).count();
    long realCount = pieces.size() - jokerCount;
    if (realCount < 2) return false;

    return switch (meld.type()) {
      case GROUP -> isValidGroup(pieces);
      case SUITE -> isValidSuite(pieces);
    };
  }

  private static boolean isValidGroup(List<Piece> pieces) {
    if (pieces.size() > 4) return false;
    List<Piece> reals = pieces.stream().filter(p -> !p.isJoker()).toList();
    int num = reals.get(0).num();
    if (!reals.stream().allMatch(p -> p.num() == num)) return false;
    Set<com.remi.engine.domain.Color> colors = new HashSet<>();
    for (Piece p : reals) colors.add(p.color());
    return colors.size() == reals.size();
  }

  private static boolean isValidSuite(List<Piece> pieces) {
    if (pieces.size() > 13) return false;
    List<Piece> reals = pieces.stream().filter(p -> !p.isJoker()).toList();
    com.remi.engine.domain.Color color = reals.get(0).color();
    if (!reals.stream().allMatch(p -> p.color() == color)) return false;

    Integer base = null;
    for (int i = 0; i < pieces.size(); i++) {
      Piece p = pieces.get(i);
      if (p.isJoker()) continue;
      int candidate = p.num() - i;
      if (base == null) { base = candidate; continue; }
      if (candidate == base) continue;
      if ((p.num() + 13 - i) == base) continue;
      return false;
    }
    if (base == null) return false;

    for (int i = 0; i < pieces.size(); i++) {
      int n = base + i;
      if (n > 14) return false;
      if (n == 14) continue; // wrap (e.g., 12-13-1)
      if (n < 1) return false;
    }
    return true;
  }
}
