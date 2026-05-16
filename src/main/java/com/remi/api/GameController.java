package com.remi.api;

import com.remi.engine.domain.Action;
import com.remi.engine.domain.GameState;
import com.remi.service.GameService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/dev/games")
public class GameController {
  private final GameService service;
  public GameController(GameService service) { this.service = service; }

  @PostMapping
  public ResponseEntity<GameView> create(@Valid @RequestBody CreateGameRequest req) {
    GameState s = service.create(req.numPlayers(), req.mode(), req.difficulty(), req.seed());
    return ResponseEntity.created(URI.create("/api/dev/games/" + s.id())).body(GameView.of(s, 0));
  }

  @GetMapping("/{id}")
  public GameView get(@PathVariable UUID id, @RequestParam(defaultValue = "0") int viewer) {
    return GameView.of(service.get(id), viewer);
  }

  @PostMapping("/{id}/actions")
  public GameView apply(@PathVariable UUID id, @RequestBody Action action) {
    GameState s = service.applyAction(id, action);
    return GameView.of(s, action.playerIdx());
  }

  @PostMapping("/{id}/bot")
  public GameView runBots(@PathVariable UUID id, @RequestParam(defaultValue = "0") int viewer) {
    GameState s = service.runBotsUntilHuman(id);
    return GameView.of(s, viewer);
  }
}
