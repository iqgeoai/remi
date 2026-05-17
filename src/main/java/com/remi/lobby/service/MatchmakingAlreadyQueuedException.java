package com.remi.lobby.service;
public class MatchmakingAlreadyQueuedException extends RuntimeException {
  public MatchmakingAlreadyQueuedException() { super("User is already in matchmaking queue"); }
}
