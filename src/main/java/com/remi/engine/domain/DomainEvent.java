package com.remi.engine.domain;

import java.util.List;

public sealed interface DomainEvent {
  record TurnStarted(int playerIdx) implements DomainEvent {}
  record CardDrawn(int playerIdx, DrawSource from) implements DomainEvent {}
  record DiscardTaken(int playerIdx, int discardIdx, int taken) implements DomainEvent {}
  record PieceDiscarded(int playerIdx, int pieceId) implements DomainEvent {}
  record PlayerEtalat(int playerIdx, int totalPoints) implements DomainEvent {}
  record LayoffPlayed(int playerIdx, int meldIdx, int pieceId) implements DomainEvent {}
  record RoundClosed(int closerIdx, List<RoundResult> results, boolean withJoker) implements DomainEvent {}
  record StockExhausted() implements DomainEvent {}
}
