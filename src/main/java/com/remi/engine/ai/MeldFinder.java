package com.remi.engine.ai;

import com.remi.engine.domain.*;
import com.remi.engine.rules.MeldValidator;
import com.remi.engine.rules.Scoring;
import java.util.*;

public final class MeldFinder {
  private MeldFinder() {}

  public static List<Meld> findAnyMelds(List<Piece> hand) {
    List<Meld> found = new ArrayList<>();
    Set<Integer> used = new HashSet<>();

    // Groups by num
    Map<Integer, List<Piece>> byNum = new HashMap<>();
    for (Piece p : hand) {
      if (p.isJoker()) continue;
      byNum.computeIfAbsent(p.num(), k -> new ArrayList<>()).add(p);
    }
    Deque<Piece> jokers = new ArrayDeque<>();
    for (Piece p : hand) if (p.isJoker() && !used.contains(p.id())) jokers.add(p);

    for (var entry : byNum.entrySet()) {
      List<Piece> avail = new ArrayList<>();
      for (Piece p : entry.getValue()) if (!used.contains(p.id())) avail.add(p);
      Map<Color, Piece> byColor = new HashMap<>();
      for (Piece p : avail) byColor.putIfAbsent(p.color(), p);
      List<Piece> distinct = new ArrayList<>(byColor.values());
      if (distinct.size() >= 3) {
        List<Piece> grp = distinct.subList(0, Math.min(4, distinct.size()));
        Map<Integer, Integer> placedBy = new HashMap<>();
        Meld m = new Meld(0, MeldType.GROUP, grp, placedBy);
        if (MeldValidator.isValid(m)) {
          grp.forEach(p -> used.add(p.id()));
          found.add(m);
        }
      } else if (distinct.size() == 2 && !jokers.isEmpty()) {
        Piece j = jokers.pop();
        List<Piece> grp = new ArrayList<>(distinct); grp.add(j);
        Meld m = new Meld(0, MeldType.GROUP, grp, new HashMap<>());
        if (MeldValidator.isValid(m)) {
          grp.forEach(p -> used.add(p.id()));
          found.add(m);
        }
      }
    }

    // Suites by color
    Map<Color, List<Piece>> byColor = new HashMap<>();
    for (Piece p : hand) {
      if (p.isJoker() || used.contains(p.id())) continue;
      byColor.computeIfAbsent(p.color(), k -> new ArrayList<>()).add(p);
    }
    for (var entry : byColor.entrySet()) {
      List<Piece> list = new ArrayList<>(entry.getValue());
      list.sort(Comparator.comparingInt(Piece::num));
      List<Piece> run = new ArrayList<>();
      for (Piece p : list) {
        if (run.isEmpty() || p.num() == run.get(run.size() - 1).num() + 1) run.add(p);
        else if (p.num() == run.get(run.size() - 1).num()) continue;
        else {
          if (run.size() >= 3) {
            Meld m = new Meld(0, MeldType.SUITE, new ArrayList<>(run), new HashMap<>());
            run.forEach(pp -> used.add(pp.id()));
            found.add(m);
          }
          run = new ArrayList<>(); run.add(p);
        }
      }
      if (run.size() >= 3) {
        Meld m = new Meld(0, MeldType.SUITE, new ArrayList<>(run), new HashMap<>());
        run.forEach(pp -> used.add(pp.id()));
        found.add(m);
      }
    }
    return found;
  }

  public static List<Meld> findFirstMeldSet(List<Piece> hand) {
    List<Meld> all = findAnyMelds(hand);
    List<Meld> suites = new ArrayList<>();
    List<Meld> groups = new ArrayList<>();
    for (Meld m : all) { if (m.type() == MeldType.SUITE) suites.add(m); else groups.add(m); }
    List<Meld> groupsDe1 = new ArrayList<>();
    for (Meld m : groups) if (m.pieces().stream().anyMatch(p -> !p.isJoker() && p.num() == 1)) groupsDe1.add(m);

    if (!suites.isEmpty()) {
      suites.sort((a, b) -> totalFirst(b) - totalFirst(a));
      List<Meld> chosen = new ArrayList<>(); chosen.add(suites.get(0));
      int total = totalFirst(suites.get(0));
      List<Meld> candidates = new ArrayList<>();
      candidates.addAll(suites.subList(1, suites.size()));
      candidates.addAll(groups);
      candidates.sort((a, b) -> totalFirst(b) - totalFirst(a));
      for (Meld c : candidates) {
        if (total >= 45) break;
        chosen.add(c); total += totalFirst(c);
      }
      if (total >= 45) return chosen;
    }
    if (!groupsDe1.isEmpty()) {
      List<Meld> chosen = new ArrayList<>(); chosen.add(groupsDe1.get(0));
      int total = totalFirst(groupsDe1.get(0));
      List<Meld> rest = new ArrayList<>();
      for (Meld m : groups) if (m != groupsDe1.get(0)) rest.add(m);
      rest.addAll(suites);
      for (Meld c : rest) {
        if (total >= 45) break;
        chosen.add(c); total += totalFirst(c);
      }
      if (total >= 45) return chosen;
    }
    return null;
  }

  private static int totalFirst(Meld m) {
    return m.pieces().stream().mapToInt(p -> Scoring.firstMeldPieceValue(p, m)).sum();
  }

  public static List<LayoffProposal> findLayoffs(List<Piece> hand, List<Meld> melds) {
    List<LayoffProposal> out = new ArrayList<>();
    Set<Integer> usedIds = new HashSet<>();
    for (int mi = 0; mi < melds.size(); mi++) {
      Meld meld = melds.get(mi);
      for (Piece piece : hand) {
        if (usedIds.contains(piece.id())) continue;
        for (int pos = 0; pos <= meld.pieces().size(); pos++) {
          List<Piece> trial = new ArrayList<>(meld.pieces());
          trial.add(pos, piece);
          Meld candidate = new Meld(meld.owner(), meld.type(), trial, meld.placedBy());
          if (MeldValidator.isValid(candidate)) {
            out.add(new LayoffProposal(piece.id(), mi));
            usedIds.add(piece.id());
            break;
          }
        }
      }
    }
    return out;
  }
}
