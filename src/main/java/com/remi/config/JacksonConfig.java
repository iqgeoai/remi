package com.remi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.remi.engine.domain.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {
  @Bean
  @Primary
  public ObjectMapper objectMapper() {
    return JsonMapper.builder()
        .findAndAddModules()
        .addMixIn(Action.class, ActionMixIn.class)
        .addMixIn(ActionResult.class, ActionResultMixIn.class)
        .addMixIn(DomainEvent.class, DomainEventMixIn.class)
        .build();
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = Action.DrawFromStock.class, name = "DRAW_FROM_STOCK"),
    @JsonSubTypes.Type(value = Action.TakeDiscard.class, name = "TAKE_DISCARD"),
    @JsonSubTypes.Type(value = Action.Etalat.class, name = "ETALAT"),
    @JsonSubTypes.Type(value = Action.Layoff.class, name = "LAYOFF"),
    @JsonSubTypes.Type(value = Action.Discard.class, name = "DISCARD"),
    @JsonSubTypes.Type(value = Action.ForceAutoAction.class, name = "FORCE_AUTO")
  })
  abstract static class ActionMixIn {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = ActionResult.Accepted.class, name = "ACCEPTED"),
    @JsonSubTypes.Type(value = ActionResult.Rejected.class, name = "REJECTED")
  })
  abstract static class ActionResultMixIn {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = DomainEvent.TurnStarted.class, name = "TURN_STARTED"),
    @JsonSubTypes.Type(value = DomainEvent.CardDrawn.class, name = "CARD_DRAWN"),
    @JsonSubTypes.Type(value = DomainEvent.DiscardTaken.class, name = "DISCARD_TAKEN"),
    @JsonSubTypes.Type(value = DomainEvent.PieceDiscarded.class, name = "PIECE_DISCARDED"),
    @JsonSubTypes.Type(value = DomainEvent.PlayerEtalat.class, name = "PLAYER_ETALAT"),
    @JsonSubTypes.Type(value = DomainEvent.LayoffPlayed.class, name = "LAYOFF_PLAYED"),
    @JsonSubTypes.Type(value = DomainEvent.RoundClosed.class, name = "ROUND_CLOSED"),
    @JsonSubTypes.Type(value = DomainEvent.StockExhausted.class, name = "STOCK_EXHAUSTED")
  })
  abstract static class DomainEventMixIn {}
}
