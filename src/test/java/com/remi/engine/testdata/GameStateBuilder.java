package com.remi.engine.testdata;

import com.remi.engine.domain.*;
import java.util.*;

public final class GameStateBuilder {
  private UUID id = UUID.randomUUID();
  private final List<Player> players = new ArrayList<>();
  private List<Piece> stock = new ArrayList<>();
  private List<Piece> discard = new ArrayList<>();
  private Piece atu = new Piece(-1, 5, Color.RED, false);
  private final List<Meld> melds = new ArrayList<>();
  private int current = 0;
  private Phase phase = Phase.DRAW;
  private DrawSource drewFrom = null;
  private int turnTaken = 0;
  private int round = 1;
  private Mode mode = Mode.ETALAT;
  private Difficulty difficulty = Difficulty.MED;
  private boolean doubleGame = false;
  private boolean closed = false;
  private List<Integer> totals = new ArrayList<>();
  private long seed = 42L;

  public static GameStateBuilder aGame() { return new GameStateBuilder(); }
  public GameStateBuilder withPlayers(String... names) {
    for (int i = 0; i < names.length; i++) {
      players.add(new Player(names[i], i > 0, new ArrayList<>(), false, false, false, null));
      totals.add(0);
    }
    return this;
  }
  public GameStateBuilder hand(int playerIdx, Piece... pieces) {
    Player p = players.get(playerIdx);
    players.set(playerIdx, new Player(p.name(), p.isBot(), List.of(pieces), p.hasEtalat(), p.calledAtu(), p.announced(), p.mustUsePieceId()));
    return this;
  }
  public GameStateBuilder etalat(int playerIdx) {
    Player p = players.get(playerIdx);
    players.set(playerIdx, new Player(p.name(), p.isBot(), p.hand(), true, p.calledAtu(), p.announced(), p.mustUsePieceId()));
    return this;
  }
  public GameStateBuilder mustUse(int playerIdx, int pieceId) {
    Player p = players.get(playerIdx);
    players.set(playerIdx, new Player(p.name(), p.isBot(), p.hand(), p.hasEtalat(), p.calledAtu(), p.announced(), pieceId));
    return this;
  }
  public GameStateBuilder stock(Piece... ps) { this.stock = new ArrayList<>(List.of(ps)); return this; }
  public GameStateBuilder discard(Piece... ps) { this.discard = new ArrayList<>(List.of(ps)); return this; }
  public GameStateBuilder atu(Piece p) { this.atu = p; return this; }
  public GameStateBuilder melds(Meld... ms) { melds.clear(); Collections.addAll(melds, ms); return this; }
  public GameStateBuilder current(int idx) { this.current = idx; return this; }
  public GameStateBuilder phase(Phase ph) { this.phase = ph; return this; }
  public GameStateBuilder drewFrom(DrawSource ds) { this.drewFrom = ds; return this; }
  public GameStateBuilder turnTaken(int n) { this.turnTaken = n; return this; }
  public GameStateBuilder mode(Mode m) { this.mode = m; return this; }
  public GameStateBuilder doubleGame(boolean d) { this.doubleGame = d; return this; }
  public GameState build() {
    return new GameState(id, players, stock, discard, atu, melds, current, phase, drewFrom,
        turnTaken, round, mode, difficulty, doubleGame, closed, totals, seed);
  }
}
