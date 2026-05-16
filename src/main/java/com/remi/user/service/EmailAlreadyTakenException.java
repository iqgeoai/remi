package com.remi.user.service;
public class EmailAlreadyTakenException extends RuntimeException {
  public EmailAlreadyTakenException(String email) { super("Email already taken: " + email); }
}
