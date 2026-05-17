package com.remi.user.api;

import com.remi.user.service.AuthService;
import com.remi.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final UserService userService;
  private final AuthService authService;

  public AuthController(UserService userService, AuthService authService) {
    this.userService = userService;
    this.authService = authService;
  }

  @PostMapping("/register")
  public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req) {
    var user = userService.register(req.email(), req.username(), req.password());
    return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.of(user));
  }

  @PostMapping("/verify-email")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
    userService.verifyEmail(req.token());
  }

  @PostMapping("/login")
  public AuthTokensResponse login(@Valid @RequestBody LoginRequest req) {
    return AuthTokensResponse.of(authService.login(req.emailOrUsername(), req.password()));
  }

  @PostMapping("/refresh")
  public AuthTokensResponse refresh(@Valid @RequestBody RefreshRequest req) {
    return AuthTokensResponse.of(authService.refresh(req.refreshToken()));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@Valid @RequestBody LogoutRequest req) {
    authService.logout(req.refreshToken());
  }

  @PostMapping("/request-password-reset")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void requestPasswordReset(@Valid @RequestBody RequestPasswordResetRequest req) {
    userService.requestPasswordReset(req.email());
  }

  @PostMapping("/reset-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
    userService.resetPassword(req.token(), req.newPassword());
  }
}
