package com.remi.user.service;
public class InvalidTokenException extends RuntimeException {
  public enum Kind { VERIFICATION, REFRESH, PASSWORD_RESET }
  private final Kind kind;
  public InvalidTokenException(Kind kind, String reason) { super(reason); this.kind = kind; }
  public Kind getKind() { return kind; }
}
