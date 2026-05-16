package com.remi.engine.testdata;

import com.remi.engine.domain.*;
import java.util.*;

public final class MeldBuilder {
  private int owner = 0;
  private MeldType type = MeldType.GROUP;
  private final List<Piece> pieces = new ArrayList<>();
  private final Map<Integer, Integer> placedBy = new HashMap<>();

  public static MeldBuilder group(Piece... ps) {
    MeldBuilder b = new MeldBuilder();
    b.type = MeldType.GROUP;
    Collections.addAll(b.pieces, ps);
    return b;
  }
  public static MeldBuilder suite(Piece... ps) {
    MeldBuilder b = new MeldBuilder();
    b.type = MeldType.SUITE;
    Collections.addAll(b.pieces, ps);
    return b;
  }
  public MeldBuilder owner(int o) { this.owner = o; return this; }
  public MeldBuilder placedBy(int pieceId, int playerIdx) { placedBy.put(pieceId, playerIdx); return this; }
  public Meld build() { return new Meld(owner, type, pieces, placedBy); }
}
