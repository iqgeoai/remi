package com.remi.lobby.service;
public class AlreadySeatedException extends RuntimeException {
  public AlreadySeatedException() { super("User already seated at this lobby"); }
}
