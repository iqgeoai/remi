package com.remi.engine.rules;

import com.remi.engine.domain.*;
import java.util.*;

public final class Dealer {
  private static final List<String> AI_NAMES = List.of("Ana", "Mihai", "Elena", "Radu", "Sorin");
  private Dealer() {}

  public static GameState deal(int numPlayers, Mode mode, Difficulty difficulty, long seed) {
    if (numPlayers < 2 || numPlayers > 6) {
      throw new IllegalArgumentException("numPlayers must be 2..6");
    }
    Random rng = new Random(seed);
    List<Piece> deck = makeDeck();
    Collections.shuffle(deck, rng);

    List<String> aiNames = new ArrayList<>(AI_NAMES);
    Collections.shuffle(aiNames, rng);

    List<Player> players = new ArrayList<>();
    List<List<Piece>> hands = new ArrayList<>();
    for (int i = 0; i < numPlayers; i++) hands.add(new ArrayList<>());
    for (int r = 0; r < 14; r++) {
      for (int i = 0; i < numPlayers; i++) {
        hands.get(i).add(deck.remove(deck.size() - 1));
      }
    }
    // First player gets one more piece
    hands.get(0).add(deck.remove(deck.size() - 1));

    Piece atu = deck.remove(deck.size() - 1);
    List<Piece> stock = deck;

    for (int i = 0; i < numPlayers; i++) {
      String name = (i == 0) ? "Tu" : aiNames.get(i - 1);
      boolean isBot = (i != 0);
      boolean calledAtu = hands.get(i).stream().anyMatch(p -> Piece.sameValue(p, atu));
      players.add(new Player(name, isBot, hands.get(i), false, calledAtu, false, null));
    }

    boolean doubleGame = atu.isJoker() || atu.num() == 1;

    List<Integer> totals = new ArrayList<>();
    for (int i = 0; i < numPlayers; i++) totals.add(0);

    return new GameState(
        UUID.randomUUID(), players, stock, List.of(), atu, List.of(),
        0, Phase.DISCARD, DrawSource.STOCK, 0, 1,
        mode, difficulty, doubleGame, false, totals, seed);
  }

  private static List<Piece> makeDeck() {
    List<Piece> deck = new ArrayList<>(106);
    int id = 0;
    for (Color c : List.of(Color.RED, Color.YELLOW, Color.BLUE, Color.BLACK)) {
      for (int set = 0; set < 2; set++) {
        for (int n = 1; n <= 13; n++) {
          deck.add(new Piece(id++, n, c, false));
        }
      }
    }
    deck.add(new Piece(id++, 0, Color.JOKER, true));
    deck.add(new Piece(id++, 0, Color.JOKER, true));
    return deck;
  }
}
