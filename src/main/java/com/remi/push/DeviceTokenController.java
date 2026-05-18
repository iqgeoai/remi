package com.remi.push;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/push")
public class DeviceTokenController {
    private final DeviceTokenRepository repo;

    public DeviceTokenController(DeviceTokenRepository repo) {
        this.repo = repo;
    }

    public record RegisterReq(String token, String platform) {}

    @PostMapping("/device-token")
    public ResponseEntity<Void> register(
        @AuthenticationPrincipal UUID userId,
        @RequestBody RegisterReq req
    ) {
        repo.findByUserIdAndToken(userId, req.token())
            .orElseGet(() -> repo.save(new DeviceToken(userId, req.token(), req.platform())));
        return ResponseEntity.noContent().build();
    }
}
