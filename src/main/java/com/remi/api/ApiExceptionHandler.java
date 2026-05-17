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

  @ExceptionHandler(com.remi.user.service.EmailAlreadyTakenException.class)
  public ResponseEntity<ApiError> emailTaken(com.remi.user.service.EmailAlreadyTakenException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
        .body(new ApiError("EMAIL_TAKEN", "Acest email este deja folosit."));
  }

  @ExceptionHandler(com.remi.user.service.UsernameAlreadyTakenException.class)
  public ResponseEntity<ApiError> usernameTaken(com.remi.user.service.UsernameAlreadyTakenException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
        .body(new ApiError("USERNAME_TAKEN", "Acest username este deja folosit."));
  }

  @ExceptionHandler(com.remi.user.service.InvalidCredentialsException.class)
  public ResponseEntity<ApiError> invalidCreds(com.remi.user.service.InvalidCredentialsException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST)
        .body(new ApiError("INVALID_CREDENTIALS", "Credențiale invalide sau email neverificat."));
  }

  @ExceptionHandler(com.remi.user.service.InvalidTokenException.class)
  public ResponseEntity<ApiError> invalidToken(com.remi.user.service.InvalidTokenException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST)
        .body(new ApiError("INVALID_TOKEN", "Token invalid sau expirat."));
  }

  @ExceptionHandler(com.remi.user.service.TokenReusedException.class)
  public ResponseEntity<ApiError> tokenReused(com.remi.user.service.TokenReusedException e) {
    log.warn("Refresh token reuse detected — all user sessions revoked");
    return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
        .body(new ApiError("TOKEN_REUSED", "Sesiunea a fost compromisă, re-autentificare necesară."));
  }

  @ExceptionHandler(com.remi.user.service.UserNotFoundException.class)
  public ResponseEntity<ApiError> userNotFound(com.remi.user.service.UserNotFoundException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
        .body(new ApiError("USER_NOT_FOUND", "Utilizator inexistent."));
  }

  @ExceptionHandler(com.remi.user.service.PasswordPolicyViolationException.class)
  public ResponseEntity<ApiError> passwordPolicy(com.remi.user.service.PasswordPolicyViolationException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST)
        .body(new ApiError("PASSWORD_POLICY", e.getMessage()));
  }

  @ExceptionHandler(com.remi.user.service.UsernamePolicyViolationException.class)
  public ResponseEntity<ApiError> usernamePolicy(com.remi.user.service.UsernamePolicyViolationException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST)
        .body(new ApiError("USERNAME_POLICY", e.getMessage()));
  }

  @ExceptionHandler(com.remi.lobby.service.LobbyNotFoundException.class)
  public ResponseEntity<ApiError> lobbyNotFound(com.remi.lobby.service.LobbyNotFoundException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
        .body(new ApiError("LOBBY_NOT_FOUND", "Lobby inexistent."));
  }
  @ExceptionHandler(com.remi.lobby.service.LobbyFullException.class)
  public ResponseEntity<ApiError> lobbyFull(com.remi.lobby.service.LobbyFullException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
        .body(new ApiError("LOBBY_FULL", "Lobby plin."));
  }
  @ExceptionHandler(com.remi.lobby.service.AlreadySeatedException.class)
  public ResponseEntity<ApiError> alreadySeated(com.remi.lobby.service.AlreadySeatedException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
        .body(new ApiError("ALREADY_SEATED", "Ești deja la această masă."));
  }
  @ExceptionHandler(com.remi.lobby.service.NotSeatedException.class)
  public ResponseEntity<ApiError> notSeated(com.remi.lobby.service.NotSeatedException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
        .body(new ApiError("NOT_SEATED", "Nu ești la această masă."));
  }
  @ExceptionHandler(com.remi.lobby.service.NotYourSeatException.class)
  public ResponseEntity<ApiError> notYourSeat(com.remi.lobby.service.NotYourSeatException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
        .body(new ApiError("NOT_YOUR_SEAT", "Nu e locul tău."));
  }
  @ExceptionHandler(com.remi.lobby.service.GameAlreadyStartedException.class)
  public ResponseEntity<ApiError> alreadyStarted(com.remi.lobby.service.GameAlreadyStartedException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
        .body(new ApiError("GAME_ALREADY_STARTED", "Jocul a început deja."));
  }
  @ExceptionHandler(com.remi.lobby.service.JoinCodeNotFoundException.class)
  public ResponseEntity<ApiError> codeNotFound(com.remi.lobby.service.JoinCodeNotFoundException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
        .body(new ApiError("JOIN_CODE_NOT_FOUND", "Cod invalid."));
  }
  @ExceptionHandler(com.remi.lobby.service.MatchmakingAlreadyQueuedException.class)
  public ResponseEntity<ApiError> alreadyQueued(com.remi.lobby.service.MatchmakingAlreadyQueuedException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
        .body(new ApiError("ALREADY_QUEUED", "Ești deja în coada de matchmaking."));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> unexpected(Exception e) {
    log.error("Unexpected", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("INTERNAL_ERROR", "Eroare neașteptată."));
  }
}
