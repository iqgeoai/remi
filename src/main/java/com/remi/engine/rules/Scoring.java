package com.remi.engine.rules;

import com.remi.engine.domain.*;
import java.util.List;

public final class Scoring {
  private Scoring() {}

  public static int finalPieceValue(Piece p) {
    if (p.isJoker()) return 50;
    if (p.num() == 1) return 25;
    if (p.num() >= 2 && p.num() <= 9) return 5;
    return 10;
  }

  public static int firstMeldPieceValue(Piece piece, Meld meld) {
    if (!piece.isJoker() && piece.num() >= 2 && piece.num() <= 9) return 5;
    if (!piece.isJoker() && piece.num() >= 10 && piece.num() <= 13) return 10;
    if (!piece.isJoker() && piece.num() == 1) {
      if (meld.type() == MeldType.GROUP) return 25;
      int idx = meld.pieces().indexOf(piece);
      if (idx == 0) return 5;
      if (idx == meld.pieces().size() - 1) return 10;
      return 5;
    }
    // joker
    if (meld.type() == MeldType.GROUP) {
      Piece ref = meld.pieces().stream().filter(p -> !p.isJoker()).findFirst().orElse(null);
      if (ref == null) return 10;
      if (ref.num() == 1) return 25;
      if (ref.num() >= 2 && ref.num() <= 9) return 5;
      return 10;
    }
    Integer repNum = inferJokerNumber(meld, piece);
    if (repNum != null && repNum == 1) {
      int idx = meld.pieces().indexOf(piece);
      return idx == 0 ? 5 : 10;
    }
    if (repNum != null && repNum >= 2 && repNum <= 9) return 5;
    return 10;
  }

  public static Integer inferJokerNumber(Meld meld, Piece jokerPiece) {
    if (meld.type() == MeldType.GROUP) {
      Piece ref = meld.pieces().stream().filter(p -> !p.isJoker()).findFirst().orElse(null);
      return ref == null ? null : ref.num();
    }
    int idx = meld.pieces().indexOf(jokerPiece);
    List<Piece> pieces = meld.pieces();
    for (int i = 0; i < pieces.size(); i++) {
      if (!pieces.get(i).isJoker()) {
        int diff = idx - i;
        int n = pieces.get(i).num() + diff;
        if (n > 13) n = ((n - 1) % 13) + 1;
        if (n < 1) n = 13 - (-n);
        return n;
      }
    }
    return null;
  }
}
