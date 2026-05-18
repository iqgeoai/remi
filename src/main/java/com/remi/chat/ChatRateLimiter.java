package com.remi.chat;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatRateLimiter {
    private static final int LIMIT = 10;
    private static final long WINDOW_MS = 10_000;

    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    public boolean allow(UUID userId, String channelKey) {
        String key = userId + ":" + channelKey;
        long now = System.currentTimeMillis();
        Deque<Long> q = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) q.pollFirst();
            if (q.size() >= LIMIT) return false;
            q.addLast(now);
            return true;
        }
    }

    public void clearAllForTest() { windows.clear(); }
}
