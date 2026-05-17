package com.remi.lobby.service;
public class JoinCodeNotFoundException extends RuntimeException {
  public JoinCodeNotFoundException(String code) { super("Join code not found: " + code); }
}
