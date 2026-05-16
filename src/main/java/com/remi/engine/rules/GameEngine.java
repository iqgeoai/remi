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
  private static ActionResult applyDrawFromStock(GameState s, Action.DrawFromStock a) { throw new UnsupportedOperationException(); }
  private static ActionResult applyTakeDiscard(GameState s, Action.TakeDiscard a)     { throw new UnsupportedOperationException(); }
  private static ActionResult applyEtalat(GameState s, Action.Etalat a)               { throw new UnsupportedOperationException(); }
  private static ActionResult applyLayoff(GameState s, Action.Layoff a)               { throw new UnsupportedOperationException(); }
  private static ActionResult applyDiscard(GameState s, Action.Discard a)             { throw new UnsupportedOperationException(); }
  private static ActionResult applyForceAuto(GameState s, Action.ForceAutoAction a)   { throw new UnsupportedOperationException(); }
}
