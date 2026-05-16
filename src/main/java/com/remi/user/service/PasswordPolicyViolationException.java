package com.remi.user.service;
public class PasswordPolicyViolationException extends RuntimeException {
  public PasswordPolicyViolationException(String msg) { super(msg); }
}
