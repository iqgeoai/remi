package com.remi.engine.domain;

import java.util.List;
import java.util.Map;

public record Meld(
    int owner,
    MeldType type,
    List<Piece> pieces,
    Map<Integer, Integer> placedBy  // pieceId -> playerIdx (for layoffs onto others' melds)
) {
  public Meld {
    pieces = List.copyOf(pieces);
    placedBy = Map.copyOf(placedBy);
  }
}
