package com.remi.engine.domain;

import java.util.List;

public sealed interface Action {
  int playerIdx();

  record DrawFromStock(int playerIdx) implements Action {}
  record TakeDiscard(int playerIdx, int discardIdx) implements Action {}
  record Etalat(int playerIdx, List<MeldProposal> melds) implements Action {
    public Etalat {
      melds = List.copyOf(melds);
    }
  }
  record Layoff(int playerIdx, List<LayoffProposal> layoffs) implements Action {
    public Layoff {
      layoffs = List.copyOf(layoffs);
    }
  }
  record Discard(int playerIdx, int pieceId) implements Action {}
  record ForceAutoAction(int playerIdx) implements Action {}
}
