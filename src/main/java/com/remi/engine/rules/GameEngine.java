package com.remi.engine.rules;

import com.remi.engine.domain.*;
import java.util.List;

public final class GameEngine {
  private GameEngine() {}

  public static ActionResult apply(GameState state, Action action) {
    if (state.closed()) return reject(RejectReason.GAME_CLOSED, "Runda este închisă.");
    if (action.playerIdx() != state.current()) return reject(RejectReason.NOT_YOUR_TURN, "Nu e rândul tău.");

    return switch (action) {
      case Action.DrawFromStock a   -> applyDrawFromStock(state, a);
      case Action.TakeDiscard a     -> applyTakeDiscard(state, a);
      case Action.Etalat a          -> applyEtalat(state, a);
      case Action.Layoff a          -> applyLayoff(state, a);
      case Action.Discard a         -> applyDiscard(state, a);
      case Action.ForceAutoAction a -> applyForceAuto(state, a);
    };
  }

  static ActionResult reject(RejectReason code, String msg) {
    return new ActionResult.Rejected(code, msg);
  }

  static ActionResult.Accepted accept(GameState s, DomainEvent... events) {
    return new ActionResult.Accepted(s, List.of(events));
  }

  // Stubs — implemented in subsequent tasks
  private static ActionResult applyDrawFromStock(GameState s, Action.DrawFromStock a) {
    if (s.phase() != Phase.DRAW) return reject(RejectReason.WRONG_PHASE, "Nu poți trage acum.");
    if (s.stock().isEmpty()) return reject(RejectReason.STOCK_EMPTY, "Grămada este goală.");
    var newStock = new java.util.ArrayList<>(s.stock());
    Piece top = newStock.remove(newStock.size() - 1);
    Player p = s.players().get(s.current());
    var newHand = new java.util.ArrayList<>(p.hand()); newHand.add(top);
    var newPlayer = new Player(p.name(), p.isBot(), newHand, p.hasEtalat(), p.calledAtu(), p.announced(), p.mustUsePieceId());
    var newPlayers = new java.util.ArrayList<>(s.players()); newPlayers.set(s.current(), newPlayer);
    GameState ns = new GameState(s.id(), newPlayers, newStock, s.discard(), s.atu(), s.melds(),
        s.current(), Phase.ACTION, DrawSource.STOCK, s.turnTaken(), s.round(), s.mode(), s.difficulty(),
        s.doubleGame(), s.closed(), s.totals(), s.seed());
    return accept(ns, new DomainEvent.CardDrawn(s.current(), DrawSource.STOCK));
  }
  private static ActionResult applyTakeDiscard(GameState s, Action.TakeDiscard a) {
    if (s.phase() != Phase.DRAW) return reject(RejectReason.WRONG_PHASE, "Nu poți trage acum.");
    if (s.discard().isEmpty()) return reject(RejectReason.DISCARD_EMPTY, "Nu este nicio piesă aruncată.");
    int idx = a.discardIdx();
    if (idx < 0 || idx >= s.discard().size()) return reject(RejectReason.DISCARD_EMPTY, "Index discard invalid.");
    boolean isTop = (idx == s.discard().size() - 1);
    if (idx == 0 && s.discard().size() > 1)
      return reject(RejectReason.CANNOT_TAKE_OPENING_PIECE, "Nu poți lua piesa de start.");

    Player p = s.players().get(s.current());
    if (p.hand().size() <= 2 && !isTop)
      return reject(RejectReason.CANNOT_BREAK_LINE, "Prea puține piese pentru a rupe șirul.");
    if (p.hand().size() == 3 && !isTop)
      return reject(RejectReason.CANNOT_BREAK_LINE, "Cu 3 piese poți lua doar ultima.");
    if (!isTop) {
      if (!p.hasEtalat()) return reject(RejectReason.BREAK_REQUIRES_ETALAT, "Trebuie să fii etalat pentru a rupe șirul.");
      if (p.hand().size() < 4) return reject(RejectReason.CANNOT_BREAK_LINE, "Necesită ≥4 piese.");
    }

    var newDiscard = new java.util.ArrayList<>(s.discard());
    var taken = new java.util.ArrayList<Piece>();
    for (int i = newDiscard.size() - 1; i >= idx; i--) taken.add(0, newDiscard.remove(i));
    Piece chosen = taken.get(0);

    var newHand = new java.util.ArrayList<>(p.hand());
    newHand.addAll(taken);
    var newPlayer = new Player(p.name(), p.isBot(), newHand, p.hasEtalat(), p.calledAtu(), p.announced(), chosen.id());
    var newPlayers = new java.util.ArrayList<>(s.players()); newPlayers.set(s.current(), newPlayer);

    GameState ns = new GameState(s.id(), newPlayers, s.stock(), newDiscard, s.atu(), s.melds(),
        s.current(), Phase.ACTION, DrawSource.DISCARD, s.turnTaken(), s.round(), s.mode(), s.difficulty(),
        s.doubleGame(), s.closed(), s.totals(), s.seed());
    return accept(ns, new DomainEvent.DiscardTaken(s.current(), idx, taken.size()));
  }
  private static ActionResult applyEtalat(GameState s, Action.Etalat a)               { throw new UnsupportedOperationException(); }
  private static ActionResult applyLayoff(GameState s, Action.Layoff a)               { throw new UnsupportedOperationException(); }
  private static ActionResult applyDiscard(GameState s, Action.Discard a)             { throw new UnsupportedOperationException(); }
  private static ActionResult applyForceAuto(GameState s, Action.ForceAutoAction a)   { throw new UnsupportedOperationException(); }
}
