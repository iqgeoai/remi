package com.remi.lobby.service;
public class NotSeatedException extends RuntimeException {
  public NotSeatedException() { super("User is not seated at this game"); }
}
