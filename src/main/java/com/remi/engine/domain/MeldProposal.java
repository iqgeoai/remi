package com.remi.engine.domain;

import java.util.List;

public record MeldProposal(MeldType type, List<Integer> pieceIds) {
  public MeldProposal {
    pieceIds = List.copyOf(pieceIds);
  }
}
