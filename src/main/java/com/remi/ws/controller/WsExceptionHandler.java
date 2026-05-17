package com.remi.ws.controller;

import com.remi.api.ApiError;
import com.remi.lobby.service.NotSeatedException;
import com.remi.lobby.service.NotYourSeatException;
import com.remi.service.GameRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
public class WsExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(WsExceptionHandler.class);

  @MessageExceptionHandler(GameRuleException.class)
  @SendToUser("/queue/errors")
  public ApiError ruleViolation(GameRuleException e) {
    log.warn("WS rule violation: {} — {}", e.getCode(), e.getMessage());
    return new ApiError(e.getCode().name(), e.getMessage());
  }

  @MessageExceptionHandler({NotSeatedException.class, NotYourSeatException.class})
  @SendToUser("/queue/errors")
  public ApiError seatError(RuntimeException e) {
    String code = (e instanceof NotSeatedException) ? "NOT_SEATED" : "NOT_YOUR_SEAT";
    log.warn("WS {}: {}", code, e.getMessage());
    return new ApiError(code, e.getMessage());
  }

  @MessageExceptionHandler(Exception.class)
  @SendToUser("/queue/errors")
  public ApiError unexpected(Exception e) {
    log.error("WS unexpected error", e);
    return new ApiError("INTERNAL_ERROR", "Eroare neașteptată.");
  }
}
