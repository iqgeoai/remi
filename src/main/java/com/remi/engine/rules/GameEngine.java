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
  private static ActionResult applyTakeDiscard(GameState s, Action.TakeDiscard a)     { throw new UnsupportedOperationException(); }
  private static ActionResult applyEtalat(GameState s, Action.Etalat a)               { throw new UnsupportedOperationException(); }
  private static ActionResult applyLayoff(GameState s, Action.Layoff a)               { throw new UnsupportedOperationException(); }
  private static ActionResult applyDiscard(GameState s, Action.Discard a)             { throw new UnsupportedOperationException(); }
  private static ActionResult applyForceAuto(GameState s, Action.ForceAutoAction a)   { throw new UnsupportedOperationException(); }
}
