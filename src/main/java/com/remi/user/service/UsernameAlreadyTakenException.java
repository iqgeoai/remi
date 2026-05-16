package com.remi.user.service;
public class UsernameAlreadyTakenException extends RuntimeException {
  public UsernameAlreadyTakenException(String username) { super("Username already taken: " + username); }
}
