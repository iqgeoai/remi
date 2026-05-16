package com.remi.engine.testdata;

import com.remi.engine.domain.Color;
import com.remi.engine.domain.Piece;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class Pieces {
  private static final AtomicInteger SEQ = new AtomicInteger(0);
  private Pieces() {}
  public static int nextId() { return SEQ.getAndIncrement(); }
  public static Piece p(int num, Color color) { return new Piece(nextId(), num, color, false); }
  public static Piece joker() { return new Piece(nextId(), 0, Color.JOKER, true); }
  public static List<Piece> hand(Piece... pieces) { return new ArrayList<>(List.of(pieces)); }
}
