package com.remi.service;
import java.util.UUID;
public class GameNotFoundException extends RuntimeException {
  public GameNotFoundException(UUID id) { super("Joc inexistent: " + id); }
}
