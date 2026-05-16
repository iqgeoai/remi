package com.remi.api;
import com.remi.engine.domain.DomainEvent;
import java.util.List;
public record ApplyResponse(GameView view, List<DomainEvent> events) {}
