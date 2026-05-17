package com.remi.lobby.service;
import java.util.UUID;
public class LobbyNotFoundException extends RuntimeException {
  public LobbyNotFoundException(UUID id) { super("Lobby not found: " + id); }
}
