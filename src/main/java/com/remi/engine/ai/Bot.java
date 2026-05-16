package com.remi.engine.ai;

import com.remi.engine.domain.*;
import com.remi.engine.rules.Scoring;
import java.util.*;

public final class Bot {
  private Bot() {}

  public static Action decide(GameState state, int playerIdx) {
    Player p = state.players().get(playerIdx);
    if (state.phase() == Phase.DRAW) {
      return new Action.DrawFromStock(playerIdx);
    }

    if (state.mode() == Mode.TABLA) {
      List<Meld> melds = MeldFinder.findAnyMelds(p.hand());
      Set<Integer> usedIds = new HashSet<>();
      for (Meld m : melds) for (Piece piece : m.pieces()) usedIds.add(piece.id());
      List<Piece> uncovered = new ArrayList<>();
      for (Piece piece : p.hand()) if (!usedIds.contains(piece.id())) uncovered.add(piece);
      if (!melds.isEmpty() && uncovered.size() <= 1) {
        if (uncovered.isEmpty()) return new Action.Discard(playerIdx, p.hand().get(0).id());
        return new Action.Discard(playerIdx, uncovered.get(0).id());
      }
    } else {
      if (!p.hasEtalat()) {
        List<Meld> first = MeldFinder.findFirstMeldSet(p.hand());
        if (first != null) {
          List<MeldProposal> proposals = new ArrayList<>();
          for (Meld m : first) {
            List<Integer> ids = new ArrayList<>();
            for (Piece piece : m.pieces()) ids.add(piece.id());
            proposals.add(new MeldProposal(m.type(), ids));
          }
          return new Action.Etalat(playerIdx, proposals);
        }
      } else {
        List<Meld> more = MeldFinder.findAnyMelds(p.hand());
        if (!more.isEmpty()) {
          Meld m = more.get(0);
          List<Integer> ids = new ArrayList<>();
          for (Piece piece : m.pieces()) ids.add(piece.id());
          return new Action.Etalat(playerIdx, List.of(new MeldProposal(m.type(), ids)));
        }
        List<LayoffProposal> los = MeldFinder.findLayoffs(p.hand(), state.melds());
        if (!los.isEmpty()) return new Action.Layoff(playerIdx, los);
      }
    }

    // Fallback: discard
    Piece pick = chooseDiscard(p);
    return new Action.Discard(playerIdx, pick.id());
  }

  static Piece chooseDiscard(Player p) {
    Piece best = null;
    for (Piece piece : p.hand()) {
      if (piece.isJoker()) continue;
      if (p.mustUsePieceId() != null && piece.id() == p.mustUsePieceId()) continue;
      if (best == null || Scoring.finalPieceValue(piece) > Scoring.finalPieceValue(best)) best = piece;
    }
    return best != null ? best : p.hand().get(0);
  }
}
