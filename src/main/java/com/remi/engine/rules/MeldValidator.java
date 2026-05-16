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
    return false; // implemented in next task
  }
}
