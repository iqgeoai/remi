package com.remi.user.api;

import com.remi.user.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {
  private final UserService userService;
  public UserController(UserService userService) { this.userService = userService; }

  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal UUID userId) {
    return UserResponse.of(userService.getById(userId));
  }
}
