package com.remi.ws.controller;

import com.remi.engine.domain.Action;
import com.remi.service.GameService;
import com.remi.ws.config.StompPrincipal;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.security.Principal;
import java.util.UUID;

@Controller
public class GameWsController {
  private final GameService gameService;

  public GameWsController(GameService gameService) { this.gameService = gameService; }

  @MessageMapping("/games/{gameId}/actions")
  public void handleAction(@DestinationVariable UUID gameId, @Payload Action action, Principal principal) {
    UUID userId = ((StompPrincipal) principal).userId();
    gameService.applyActionAsUser(gameId, userId, action);
  }
}
