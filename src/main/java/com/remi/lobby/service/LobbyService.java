package com.remi.lobby.service;

import com.remi.engine.domain.Difficulty;
import com.remi.engine.domain.Mode;
import com.remi.lobby.domain.LobbyGame;
import java.util.List;
import java.util.UUID;

public interface LobbyService {
  LobbyGame createPrivate(UUID ownerId, int numPlayers, Mode mode, Difficulty diff);
  LobbyGame createPublic(UUID ownerId, int numPlayers, Mode mode, Difficulty diff);
  LobbyGame createPublicForUsers(List<UUID> userIds, int numPlayers, Mode mode, Difficulty diff);
  LobbyGame joinByCode(UUID userId, String joinCode);
  LobbyGame joinPublic(UUID userId, UUID gameId);
  List<LobbyGame> listPublicWaiting();
  List<LobbyGame> myGames(UUID userId);
  void leave(UUID userId, UUID gameId);
  LobbyGame get(UUID gameId);
}
