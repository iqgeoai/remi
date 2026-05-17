package com.remi.lobby.service;
public class LobbyFullException extends RuntimeException {
  public LobbyFullException() { super("Lobby is full"); }
}
