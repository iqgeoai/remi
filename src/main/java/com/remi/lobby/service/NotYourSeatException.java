package com.remi.lobby.service;
public class NotYourSeatException extends RuntimeException {
  public NotYourSeatException() { super("Action playerIdx does not match user's seat"); }
}
