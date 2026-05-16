package com.remi.engine.domain;

import java.util.List;

public record Player(
    String name,
    boolean isBot,
    List<Piece> hand,
    boolean hasEtalat,
    boolean calledAtu,
    boolean announced,
    Integer mustUsePieceId  // null if none
) {
  public Player {
    hand = List.copyOf(hand);
  }
}
