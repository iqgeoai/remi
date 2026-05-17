package com.remi.service;

import com.remi.engine.ai.Bot;
import com.remi.engine.domain.*;
import com.remi.engine.rules.Dealer;
import com.remi.engine.rules.GameEngine;
import com.remi.persistence.GameEntity;
import com.remi.persistence.GameRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class GameService {
  private static final Logger log = LoggerFactory.getLogger(GameService.class);
  private static final int MAX_BOT_STEPS = 100;

  private final GameRepository repo;
  private final com.remi.lobby.persistence.GamePlayerRepository playerSeats;
  private final com.remi.ws.broadcast.GameBroadcaster broadcaster;
  private final com.remi.lobby.service.GameTimerService timer;

  public GameService(GameRepository repo,
                     com.remi.lobby.persistence.GamePlayerRepository playerSeats,
                     com.remi.ws.broadcast.GameBroadcaster broadcaster,
                     com.remi.lobby.service.GameTimerService timer) {
    this.repo = repo;
    this.playerSeats = playerSeats;
    this.broadcaster = broadcaster;
    this.timer = timer;
  }

  @Transactional
  public GameState create(int numPlayers, Mode mode, Difficulty difficulty, Long seed) {
    long actualSeed = (seed != null) ? seed : System.nanoTime();
    GameState s = Dealer.deal(numPlayers, mode, difficulty, actualSeed);
    repo.save(new GameEntity(s.id(), s));
    log.info("Created game {} numPlayers={} mode={} seed={}", s.id(), numPlayers, mode, actualSeed);
    return s;
  }

  @Transactional(readOnly = true)
  public GameState get(UUID gameId) {
    return repo.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId)).getState();
  }

  @Transactional
  public GameState applyAction(UUID gameId, Action action) {
    GameEntity entity = repo.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
    ActionResult result = GameEngine.apply(entity.getState(), action);
    return switch (result) {
      case ActionResult.Rejected r -> {
        log.warn("Rejected action on game {} code={}", gameId, r.code());
        throw new GameRuleException(r.code(), r.message());
      }
      case ActionResult.Accepted a -> {
        entity.setState(a.newState());
        repo.save(entity);
        log.debug("Action {} accepted on game {}", action.getClass().getSimpleName(), gameId);
        com.remi.engine.domain.GameState ns = a.newState();
        timer.cancel(gameId);
        if (!ns.closed() && !ns.players().get(ns.current()).isBot()) {
          int currentIdx = ns.current();
          timer.scheduleHardTimeout(gameId, currentIdx, () -> autoForceOnTimeout(gameId, currentIdx));
        }
        broadcaster.broadcastState(gameId, ns, a.events());
        yield ns;
      }
    };
  }

  @Transactional
  public GameState runBotsUntilHuman(UUID gameId) {
    GameEntity entity = repo.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
    GameState state = entity.getState();
    int steps = 0;
    while (!state.closed() && state.players().get(state.current()).isBot()) {
      if (steps++ >= MAX_BOT_STEPS) {
        throw new IllegalEngineStateException("Bot loop exceeded " + MAX_BOT_STEPS + " steps on game " + gameId);
      }
      Action a = Bot.decide(state, state.current());
      ActionResult r = GameEngine.apply(state, a);
      if (r instanceof ActionResult.Rejected rej) {
        throw new IllegalEngineStateException("Bot proposed rejected action: " + rej.code() + " — " + rej.message());
      }
      state = ((ActionResult.Accepted) r).newState();
    }
    entity.setState(state);
    repo.save(entity);
    return state;
  }

  @Transactional
  public com.remi.engine.domain.GameState applyActionAsUser(
      java.util.UUID gameId, java.util.UUID userId, com.remi.engine.domain.Action action) {
    int seat = playerSeats.findSeat(gameId, userId)
        .orElseThrow(com.remi.lobby.service.NotSeatedException::new);
    if (action.playerIdx() != seat) throw new com.remi.lobby.service.NotYourSeatException();
    return applyAction(gameId, action);
  }

  void autoForceOnTimeout(java.util.UUID gameId, int expectedPlayerIdx) {
    try {
      GameEntity entity = repo.findById(gameId).orElse(null);
      if (entity == null) return;
      com.remi.engine.domain.GameState s = entity.getState();
      if (s.closed() || s.current() != expectedPlayerIdx) return;
      applyAction(gameId, new com.remi.engine.domain.Action.ForceAutoAction(expectedPlayerIdx));
    } catch (Exception e) {
      log.error("autoForceOnTimeout error for game {}", gameId, e);
    }
  }
}
