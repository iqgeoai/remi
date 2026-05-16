package com.remi.user.service;
public class UsernamePolicyViolationException extends RuntimeException {
  public UsernamePolicyViolationException(String msg) { super(msg); }
}
