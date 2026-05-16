package com.remi.engine.domain;

import java.util.List;

public sealed interface ActionResult {
  record Accepted(GameState newState, List<DomainEvent> events) implements ActionResult {
    public Accepted { events = List.copyOf(events); }
  }
  record Rejected(RejectReason code, String message) implements ActionResult {}
}
