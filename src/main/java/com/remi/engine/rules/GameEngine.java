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
  private static ActionResult applyEtalat(GameState s, Action.Etalat a) {
    if (s.phase() != Phase.ACTION && s.phase() != Phase.DISCARD)
      return reject(RejectReason.WRONG_PHASE, "Nu poți etala acum.");
    Player p = s.players().get(s.current());

    java.util.List<Meld> builtMelds = new java.util.ArrayList<>();
    java.util.Set<Integer> usedIds = new java.util.HashSet<>();
    java.util.Map<Integer, Piece> handIdx = new java.util.HashMap<>();
    for (Piece piece : p.hand()) handIdx.put(piece.id(), piece);

    for (MeldProposal mp : a.melds()) {
      java.util.List<Piece> pieces = new java.util.ArrayList<>();
      for (int pid : mp.pieceIds()) {
        if (!handIdx.containsKey(pid) || usedIds.contains(pid))
          return reject(RejectReason.PIECE_NOT_IN_HAND, "Piesă inexistentă în mână.");
        pieces.add(handIdx.get(pid));
        usedIds.add(pid);
      }
      java.util.Map<Integer, Integer> placedBy = new java.util.HashMap<>();
      for (Piece piece : pieces) placedBy.put(piece.id(), s.current());
      Meld m = new Meld(s.current(), mp.type(), pieces, placedBy);
      if (!MeldValidator.isValid(m))
        return reject(RejectReason.INVALID_MELD, "Combinație invalidă.");
      builtMelds.add(m);
    }

    if (!p.hasEtalat()) {
      int totalFirst = builtMelds.stream().mapToInt(m -> m.pieces().stream()
          .mapToInt(piece -> Scoring.firstMeldPieceValue(piece, m)).sum()).sum();
      boolean hasSuite = builtMelds.stream().anyMatch(m -> m.type() == MeldType.SUITE);
      boolean has1sGroup = builtMelds.stream().anyMatch(m ->
          m.type() == MeldType.GROUP && m.pieces().stream().anyMatch(pp -> !pp.isJoker() && pp.num() == 1));
      if (!hasSuite && !has1sGroup)
        return reject(RejectReason.FIRST_MELD_NEEDS_SUITE_OR_1S, "Prima etalare necesită o suită sau o terță de 1.");
      if (totalFirst < 45)
        return reject(RejectReason.FIRST_MELD_TOO_FEW_POINTS, "Prima etalare are " + totalFirst + "p < 45p.");
    }

    var newHand = new java.util.ArrayList<>(p.hand());
    newHand.removeIf(piece -> usedIds.contains(piece.id()));
    Integer newMustUse = p.mustUsePieceId();
    if (newMustUse != null && usedIds.contains(newMustUse)) newMustUse = null;

    var newPlayer = new Player(p.name(), p.isBot(), newHand, true, p.calledAtu(), p.announced(), newMustUse);
    var newPlayers = new java.util.ArrayList<>(s.players()); newPlayers.set(s.current(), newPlayer);
    var newMelds = new java.util.ArrayList<>(s.melds()); newMelds.addAll(builtMelds);

    int totalPts = builtMelds.stream().mapToInt(m -> m.pieces().stream()
        .mapToInt(piece -> Scoring.firstMeldPieceValue(piece, m)).sum()).sum();

    GameState ns = new GameState(s.id(), newPlayers, s.stock(), s.discard(), s.atu(), newMelds,
        s.current(), s.phase(), s.drewFrom(), s.turnTaken(), s.round(), s.mode(), s.difficulty(),
        s.doubleGame(), s.closed(), s.totals(), s.seed());
    return accept(ns, new DomainEvent.PlayerEtalat(s.current(), totalPts));
  }
  private static ActionResult applyLayoff(GameState s, Action.Layoff a) {
    if (s.phase() != Phase.ACTION && s.phase() != Phase.DISCARD)
      return reject(RejectReason.WRONG_PHASE, "Nu poți lipi acum.");
    Player p = s.players().get(s.current());
    if (!p.hasEtalat()) return reject(RejectReason.NOT_ETALAT, "Trebuie să fii etalat.");

    java.util.Map<Integer, Piece> handIdx = new java.util.HashMap<>();
    for (Piece piece : p.hand()) handIdx.put(piece.id(), piece);

    java.util.List<Meld> meldsCopy = new java.util.ArrayList<>(s.melds());
    java.util.Set<Integer> placedIds = new java.util.HashSet<>();

    for (LayoffProposal lo : a.layoffs()) {
      Piece piece = handIdx.get(lo.pieceId());
      if (piece == null || placedIds.contains(lo.pieceId()))
        return reject(RejectReason.PIECE_NOT_IN_HAND, "Piesă inexistentă.");
      if (lo.meldIdx() < 0 || lo.meldIdx() >= meldsCopy.size())
        return reject(RejectReason.INVALID_LAYOFF, "Combinație inexistentă.");
      Meld target = meldsCopy.get(lo.meldIdx());
      Meld inserted = tryInsertIntoMeld(target, piece, s.current());
      if (inserted == null) return reject(RejectReason.INVALID_LAYOFF, "Piesa nu se potrivește.");
      meldsCopy.set(lo.meldIdx(), inserted);
      placedIds.add(lo.pieceId());
    }

    var newHand = new java.util.ArrayList<>(p.hand());
    newHand.removeIf(piece -> placedIds.contains(piece.id()));
    Integer newMustUse = p.mustUsePieceId();
    if (newMustUse != null && placedIds.contains(newMustUse)) newMustUse = null;

    var newPlayer = new Player(p.name(), p.isBot(), newHand, p.hasEtalat(), p.calledAtu(), p.announced(), newMustUse);
    var newPlayers = new java.util.ArrayList<>(s.players()); newPlayers.set(s.current(), newPlayer);

    GameState ns = new GameState(s.id(), newPlayers, s.stock(), s.discard(), s.atu(), meldsCopy,
        s.current(), s.phase(), s.drewFrom(), s.turnTaken(), s.round(), s.mode(), s.difficulty(),
        s.doubleGame(), s.closed(), s.totals(), s.seed());

    java.util.List<DomainEvent> evts = new java.util.ArrayList<>();
    for (LayoffProposal lo : a.layoffs())
      evts.add(new DomainEvent.LayoffPlayed(s.current(), lo.meldIdx(), lo.pieceId()));
    return new ActionResult.Accepted(ns, evts);
  }

  private static Meld tryInsertIntoMeld(Meld meld, Piece piece, int playerIdx) {
    for (int pos = 0; pos <= meld.pieces().size(); pos++) {
      var trial = new java.util.ArrayList<>(meld.pieces());
      trial.add(pos, piece);
      Meld candidate = new Meld(meld.owner(), meld.type(), trial, meld.placedBy());
      if (MeldValidator.isValid(candidate)) {
        var newPlacedBy = new java.util.HashMap<>(meld.placedBy());
        newPlacedBy.put(piece.id(), playerIdx);
        return new Meld(meld.owner(), meld.type(), trial, newPlacedBy);
      }
    }
    return null;
  }
  private static ActionResult applyDiscard(GameState s, Action.Discard a) {
    if (s.phase() != Phase.ACTION && s.phase() != Phase.DISCARD)
      return reject(RejectReason.WRONG_PHASE, "Nu poți arunca acum.");
    Player p = s.players().get(s.current());
    if (p.mustUsePieceId() != null && p.mustUsePieceId() == a.pieceId())
      return reject(RejectReason.MUST_USE_TAKEN_PIECE, "Trebuie să folosești piesa luată din șir.");
    if (p.mustUsePieceId() != null && p.hand().stream().anyMatch(piece -> piece.id() == p.mustUsePieceId()))
      return reject(RejectReason.MUST_USE_TAKEN_PIECE, "Trebuie să folosești piesa luată din șir.");

    int idx = -1;
    for (int i = 0; i < p.hand().size(); i++) if (p.hand().get(i).id() == a.pieceId()) { idx = i; break; }
    if (idx < 0) return reject(RejectReason.PIECE_NOT_IN_HAND, "Piesă inexistentă în mână.");

    var newHand = new java.util.ArrayList<>(p.hand());
    Piece discarded = newHand.remove(idx);
    var newDiscard = new java.util.ArrayList<>(s.discard()); newDiscard.add(discarded);

    boolean announce = p.announced() || newHand.size() <= 3;
    var newPlayer = new Player(p.name(), p.isBot(), newHand, p.hasEtalat(), p.calledAtu(), announce, null);
    var newPlayers = new java.util.ArrayList<>(s.players()); newPlayers.set(s.current(), newPlayer);

    GameState afterDiscard = new GameState(s.id(), newPlayers, s.stock(), newDiscard, s.atu(), s.melds(),
        s.current(), s.phase(), s.drewFrom(), s.turnTaken(), s.round(), s.mode(), s.difficulty(),
        s.doubleGame(), s.closed(), s.totals(), s.seed());

    java.util.List<DomainEvent> evts = new java.util.ArrayList<>();
    evts.add(new DomainEvent.PieceDiscarded(s.current(), discarded.id()));

    // Round-end checks
    if (newHand.isEmpty() && p.hasEtalat()) {
      return closeRound(afterDiscard, s.current(), null, evts);
    }
    if (newHand.size() == 1 && p.hasEtalat()) {
      return closeRound(afterDiscard, s.current(), newHand.get(0), evts);
    }
    if (afterDiscard.stock().isEmpty()) {
      return endRoundStockEmpty(afterDiscard, evts);
    }
    // Advance turn
    int next = (s.current() + 1) % s.players().size();
    GameState ns = new GameState(afterDiscard.id(), afterDiscard.players(), afterDiscard.stock(),
        afterDiscard.discard(), afterDiscard.atu(), afterDiscard.melds(),
        next, Phase.DRAW, null, s.turnTaken() + 1, s.round(), s.mode(), s.difficulty(),
        s.doubleGame(), false, s.totals(), s.seed());
    evts.add(new DomainEvent.TurnStarted(next));
    return new ActionResult.Accepted(ns, evts);
  }

  private static ActionResult closeRound(GameState s, int closerIdx, Piece lastDiscarded, java.util.List<DomainEvent> evts) {
    java.util.List<RoundResult> results = Scoring.closeRound(s, closerIdx, lastDiscarded);
    var newTotals = new java.util.ArrayList<>(s.totals());
    for (RoundResult r : results) newTotals.set(r.playerIdx(), newTotals.get(r.playerIdx()) + r.base());
    GameState ns = new GameState(s.id(), s.players(), s.stock(), s.discard(), s.atu(), s.melds(),
        s.current(), s.phase(), s.drewFrom(), s.turnTaken(), s.round(), s.mode(), s.difficulty(),
        s.doubleGame(), true, newTotals, s.seed());
    evts.add(new DomainEvent.RoundClosed(closerIdx, results, lastDiscarded != null && lastDiscarded.isJoker()));
    return new ActionResult.Accepted(ns, evts);
  }

  private static ActionResult endRoundStockEmpty(GameState s, java.util.List<DomainEvent> evts) {
    evts.add(new DomainEvent.StockExhausted());
    return closeRound(s, s.current(), null, evts);
  }
  private static ActionResult applyForceAuto(GameState s, Action.ForceAutoAction a) {
    if (s.phase() == Phase.DRAW) {
      if (s.stock().isEmpty()) return endRoundStockEmpty(s, new java.util.ArrayList<>());
      return applyDrawFromStock(s, new Action.DrawFromStock(s.current()));
    }
    Player p = s.players().get(s.current());
    if (p.hand().isEmpty()) return reject(RejectReason.WRONG_PHASE, "Mână goală.");

    Piece pick = null;
    for (Piece piece : p.hand()) {
      if (piece.isJoker()) continue;
      if (p.mustUsePieceId() != null && piece.id() == p.mustUsePieceId()) continue;
      if (pick == null || Scoring.finalPieceValue(piece) > Scoring.finalPieceValue(pick)) pick = piece;
    }
    if (pick == null) {
      for (Piece piece : p.hand()) {
        if (p.mustUsePieceId() != null && piece.id() == p.mustUsePieceId()) continue;
        if (pick == null || Scoring.finalPieceValue(piece) > Scoring.finalPieceValue(pick)) pick = piece;
      }
    }
    if (pick == null) pick = p.hand().get(0); // give up mustUse

    // Clear mustUse and call discard
    var newPlayer = new Player(p.name(), p.isBot(), p.hand(), p.hasEtalat(), p.calledAtu(), p.announced(), null);
    var newPlayers = new java.util.ArrayList<>(s.players()); newPlayers.set(s.current(), newPlayer);
    GameState cleared = new GameState(s.id(), newPlayers, s.stock(), s.discard(), s.atu(), s.melds(),
        s.current(), s.phase(), s.drewFrom(), s.turnTaken(), s.round(), s.mode(), s.difficulty(),
        s.doubleGame(), s.closed(), s.totals(), s.seed());
    return applyDiscard(cleared, new Action.Discard(s.current(), pick.id()));
  }
}
