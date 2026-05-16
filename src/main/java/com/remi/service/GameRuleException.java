package com.remi.service;
import com.remi.engine.domain.RejectReason;
public class GameRuleException extends RuntimeException {
  private final RejectReason code;
  public GameRuleException(RejectReason code, String msg) { super(msg); this.code = code; }
  public RejectReason getCode() { return code; }
}
