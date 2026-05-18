package com.remi.friends;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PresenceRegistry {
    private final Map<UUID, Instant> online = new ConcurrentHashMap<>();

    public void markOnline(UUID userId) {
        online.put(userId, Instant.now());
    }

    public void markOffline(UUID userId) {
        online.remove(userId);
    }

    public boolean isOnline(UUID userId) {
        return online.containsKey(userId);
    }

    public Optional<Instant> since(UUID userId) {
        return Optional.ofNullable(online.get(userId));
    }

    public Map<UUID, Instant> snapshot() {
        return Map.copyOf(online);
    }
}
