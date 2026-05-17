package com.remi.lobby.api;

import com.remi.api.GameView;
import com.remi.engine.domain.GameState;
import com.remi.lobby.domain.GameVisibility;
import com.remi.lobby.domain.LobbyGame;
import com.remi.lobby.persistence.GamePlayerRepository;
import com.remi.lobby.service.LobbyService;
import com.remi.lobby.service.NotSeatedException;
import com.remi.service.GameService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/games")
public class LobbyController {
  private final LobbyService lobby;
  private final GameService gameService;
  private final GamePlayerRepository playerSeats;

  public LobbyController(LobbyService lobby, GameService gameService, GamePlayerRepository playerSeats) {
    this.lobby = lobby;
    this.gameService = gameService;
    this.playerSeats = playerSeats;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public LobbyGame create(@Valid @RequestBody CreateGameRequest req, @AuthenticationPrincipal UUID userId) {
    return (req.visibility() == GameVisibility.PRIVATE)
        ? lobby.createPrivate(userId, req.numPlayers(), req.mode(), req.difficulty())
        : lobby.createPublic(userId, req.numPlayers(), req.mode(), req.difficulty());
  }

  @PostMapping("/{id}/join")
  public LobbyGame joinPublic(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
    return lobby.joinPublic(userId, id);
  }

  @PostMapping("/join-by-code")
  public LobbyGame joinByCode(@Valid @RequestBody JoinByCodeRequest req, @AuthenticationPrincipal UUID userId) {
    return lobby.joinByCode(userId, req.joinCode());
  }

  @GetMapping("/public")
  public List<LobbyGame> listPublic() { return lobby.listPublicWaiting(); }

  @GetMapping("/mine")
  public List<LobbyGame> mine(@AuthenticationPrincipal UUID userId) { return lobby.myGames(userId); }

  @GetMapping("/{id}")
  public GameView get(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
    int seat = playerSeats.findSeat(id, userId).orElseThrow(NotSeatedException::new);
    return GameView.of(gameService.get(id), seat);
  }

  @PostMapping("/{id}/actions")
  public GameView apply(@PathVariable UUID id, @Valid @RequestBody ActionRequest req, @AuthenticationPrincipal UUID userId) {
    GameState newState = gameService.applyActionAsUser(id, userId, req.action());
    int seat = playerSeats.findSeat(id, userId).orElseThrow();
    return GameView.of(newState, seat);
  }

  @PostMapping("/{id}/leave")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void leave(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
    lobby.leave(userId, id);
  }
}
