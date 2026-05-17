package com.remi.lobby.service;
public class GameAlreadyStartedException extends RuntimeException {
  public GameAlreadyStartedException() { super("Cannot leave: game already started"); }
}
