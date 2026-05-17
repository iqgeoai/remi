package com.remi.lobby.service;

import com.remi.engine.domain.Difficulty;
import com.remi.engine.domain.GameState;
import com.remi.engine.domain.Mode;
import com.remi.engine.rules.Dealer;
import com.remi.lobby.domain.GameVisibility;
import com.remi.lobby.domain.LobbyGame;
import com.remi.lobby.persistence.GamePlayerEntity;
import com.remi.lobby.persistence.GamePlayerRepository;
import com.remi.persistence.GameEntity;
import com.remi.persistence.GameRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class LobbyServiceImpl implements LobbyService {
  private static final int JOIN_CODE_RETRIES = 5;

  private final GameRepository games;
  private final GamePlayerRepository players;
  private final JoinCodeGenerator codeGen;
  private final Clock clock;

  public LobbyServiceImpl(GameRepository games, GamePlayerRepository players,
                          JoinCodeGenerator codeGen, Clock clock) {
    this.games = games;
    this.players = players;
    this.codeGen = codeGen;
    this.clock = clock;
  }

  @Override
  @Transactional
  public LobbyGame createPrivate(UUID ownerId, int numPlayers, Mode mode, Difficulty diff) {
    GameState s = Dealer.deal(numPlayers, mode, diff, System.nanoTime());
    GameEntity e = new GameEntity(s.id(), s);
    e.setOwnerId(ownerId);
    e.setVisibility(GameVisibility.PRIVATE);
    e.setJoinCode(generateUniqueCode());
    games.save(e);
    players.save(new GamePlayerEntity(s.id(), 0, ownerId));
    return toLobbyGame(e, 1);
  }

  @Override
  @Transactional
  public LobbyGame createPublic(UUID ownerId, int numPlayers, Mode mode, Difficulty diff) {
    GameState s = Dealer.deal(numPlayers, mode, diff, System.nanoTime());
    GameEntity e = new GameEntity(s.id(), s);
    e.setOwnerId(ownerId);
    e.setVisibility(GameVisibility.PUBLIC);
    e.setJoinCode(null);
    games.save(e);
    players.save(new GamePlayerEntity(s.id(), 0, ownerId));
    return toLobbyGame(e, 1);
  }

  @Override
  @Transactional
  public LobbyGame createPublicForUsers(List<UUID> userIds, int numPlayers, Mode mode, Difficulty diff) {
    if (userIds.size() != numPlayers) throw new IllegalArgumentException("userIds.size must equal numPlayers");
    GameState s = Dealer.deal(numPlayers, mode, diff, System.nanoTime());
    GameEntity e = new GameEntity(s.id(), s);
    e.setOwnerId(userIds.get(0));
    e.setVisibility(GameVisibility.PUBLIC);
    e.setJoinCode(null);
    games.save(e);
    for (int i = 0; i < userIds.size(); i++) {
      players.save(new GamePlayerEntity(s.id(), i, userIds.get(i)));
    }
    return toLobbyGame(e, numPlayers);
  }

  @Override
  @Transactional
  public LobbyGame joinByCode(UUID userId, String joinCode) {
    GameEntity e = games.findByJoinCode(joinCode).orElseThrow(() -> new JoinCodeNotFoundException(joinCode));
    return joinInternal(userId, e);
  }

  @Override
  @Transactional
  public LobbyGame joinPublic(UUID userId, UUID gameId) {
    GameEntity e = games.findById(gameId).orElseThrow(() -> new LobbyNotFoundException(gameId));
    if (e.getVisibility() != GameVisibility.PUBLIC) throw new LobbyNotFoundException(gameId);
    return joinInternal(userId, e);
  }

  private LobbyGame joinInternal(UUID userId, GameEntity e) {
    int numPlayers = e.getState().players().size();
    long seats = players.countByGameId(e.getId());
    if (players.existsByGameIdAndUserId(e.getId(), userId)) throw new AlreadySeatedException();
    if (seats >= numPlayers) throw new LobbyFullException();
    try {
      players.save(new GamePlayerEntity(e.getId(), (int) seats, userId));
    } catch (DataIntegrityViolationException dup) {
      throw new LobbyFullException();   // race: someone took the seat
    }
    return toLobbyGame(e, (int) seats + 1);
  }

  @Override
  @Transactional(readOnly = true)
  public List<LobbyGame> listPublicWaiting() {
    return games.findByVisibility(GameVisibility.PUBLIC).stream()
        .map(e -> toLobbyGame(e, (int) players.countByGameId(e.getId())))
        .filter(g -> g.seatsTaken() < g.numPlayers())
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<LobbyGame> myGames(UUID userId) {
    return players.findByUserId(userId).stream()
        .map(p -> games.findById(p.getGameId()).orElseThrow())
        .distinct()
        .map(e -> toLobbyGame(e, (int) players.countByGameId(e.getId())))
        .toList();
  }

  @Override
  @Transactional
  public void leave(UUID userId, UUID gameId) {
    GameEntity e = games.findById(gameId).orElseThrow(() -> new LobbyNotFoundException(gameId));
    long seats = players.countByGameId(gameId);
    if (seats >= e.getState().players().size()) throw new GameAlreadyStartedException();
    int seat = players.findSeat(gameId, userId).orElseThrow(NotSeatedException::new);
    players.deleteById(new GamePlayerEntity.PK(gameId, seat));
    if (userId.equals(e.getOwnerId())) {
      games.deleteById(gameId);     // cascade deletes remaining game_players
    }
  }

  @Override
  @Transactional(readOnly = true)
  public LobbyGame get(UUID gameId) {
    GameEntity e = games.findById(gameId).orElseThrow(() -> new LobbyNotFoundException(gameId));
    return toLobbyGame(e, (int) players.countByGameId(gameId));
  }

  private String generateUniqueCode() {
    for (int i = 0; i < JOIN_CODE_RETRIES; i++) {
      String code = codeGen.generate();
      if (games.findByJoinCode(code).isEmpty()) return code;
    }
    throw new IllegalStateException("Could not generate unique join code after " + JOIN_CODE_RETRIES + " tries");
  }

  private LobbyGame toLobbyGame(GameEntity e, int seatsTaken) {
    GameState s = e.getState();
    boolean started = seatsTaken == s.players().size();
    return new LobbyGame(
        e.getId(), e.getOwnerId(), e.getVisibility(), e.getJoinCode(),
        s.players().size(), s.mode(), s.difficulty(),
        seatsTaken, started, e.getCreatedAt()
    );
  }
}
