package com.remi.engine.domain;

import java.util.List;
import java.util.UUID;

public record GameState(
    UUID id,
    List<Player> players,
    List<Piece> stock,
    List<Piece> discard,
    Piece atu,
    List<Meld> melds,
    int current,
    Phase phase,
    DrawSource drewFrom,        // null if not drawn yet this turn
    int turnTaken,
    int round,
    Mode mode,
    Difficulty difficulty,
    boolean doubleGame,
    boolean closed,
    List<Integer> totals,
    long seed
) {
  public GameState {
    players = List.copyOf(players);
    stock = List.copyOf(stock);
    discard = List.copyOf(discard);
    melds = List.copyOf(melds);
    totals = List.copyOf(totals);
  }
}
