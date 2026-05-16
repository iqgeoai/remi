package com.remi.user.service;
public class InvalidCredentialsException extends RuntimeException {
  public InvalidCredentialsException() { super("Invalid credentials"); }
}
