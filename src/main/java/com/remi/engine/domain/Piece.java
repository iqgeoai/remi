package com.remi.engine.domain;

public record Piece(int id, int num, Color color, boolean isJoker) {
  public static boolean sameValue(Piece a, Piece b) {
    if (a.isJoker && b.isJoker) return true;
    if (a.isJoker || b.isJoker) return false;
    return a.num == b.num && a.color == b.color;
  }
}
