package com.remi.api;

import com.remi.service.GameNotFoundException;
import com.remi.service.GameRuleException;
import com.remi.service.IllegalEngineStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(GameRuleException.class)
  public ResponseEntity<ApiError> rule(GameRuleException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(e.getCode().name(), e.getMessage()));
  }

  @ExceptionHandler(GameNotFoundException.class)
  public ResponseEntity<ApiError> notFound(GameNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("GAME_NOT_FOUND", e.getMessage()));
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ApiError> race(OptimisticLockingFailureException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("GAME_VERSION_CONFLICT", "Joc actualizat între timp."));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("INVALID_REQUEST", e.getMessage()));
  }

  @ExceptionHandler(IllegalEngineStateException.class)
  public ResponseEntity<ApiError> engineBug(IllegalEngineStateException e) {
    log.error("Engine state bug", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("ENGINE_ERROR", "Eroare internă engine."));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> unexpected(Exception e) {
    log.error("Unexpected", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("INTERNAL_ERROR", "Eroare neașteptată."));
  }
}
