package com.remi.api;
import com.remi.engine.domain.*;
import java.util.*;
public record GameView(
    UUID id, List<PlayerView> players, int stockCount, List<Piece> discard, Piece atu,
    List<Meld> melds, int current, Phase phase, DrawSource drewFrom, int turnTaken,
    int round, Mode mode, Difficulty difficulty, boolean doubleGame, boolean closed,
    List<Integer> totals
) {
  public record PlayerView(String name, boolean isBot, boolean hasEtalat,
                           boolean calledAtu, boolean announced, Integer mustUsePieceId,
                           List<Piece> hand, int handCount) {}
  public static GameView of(GameState s, int viewerIdx) {
    List<PlayerView> pvs = new ArrayList<>();
    for (int i = 0; i < s.players().size(); i++) {
      Player p = s.players().get(i);
      List<Piece> visibleHand = (i == viewerIdx) ? p.hand() : List.of();
      pvs.add(new PlayerView(p.name(), p.isBot(), p.hasEtalat(), p.calledAtu(),
          p.announced(), p.mustUsePieceId(), visibleHand, p.hand().size()));
    }
    return new GameView(s.id(), pvs, s.stock().size(), s.discard(), s.atu(), s.melds(),
        s.current(), s.phase(), s.drewFrom(), s.turnTaken(), s.round(), s.mode(),
        s.difficulty(), s.doubleGame(), s.closed(), s.totals());
  }
}
