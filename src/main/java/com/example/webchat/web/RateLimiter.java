package com.example.webchat.web;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple fixed-window per-key rate limiter. No Minecraft/Fabric dependencies
 * so it can be unit tested in isolation.
 */
public class RateLimiter {
    private static final long WINDOW_MILLIS = 60_000;

    // key -> [count, windowResetTimestamp]
    private final Map<String, long[]> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int maxPerWindow, long now) {
        if (Math.random() < 0.01) {
            windows.entrySet().removeIf(entry -> entry.getValue()[1] < now);
        }

        long[] data = windows.computeIfAbsent(key, k -> new long[] { 0, now + WINDOW_MILLIS });

        // The array itself (not just the map) is shared and mutated in place, so concurrent
        // callers for the same key (multiple tabs, or just concurrent request threads) need
        // to be serialized here - the map's own thread-safety doesn't cover that.
        synchronized (data) {
            if (now > data[1]) {
                data[0] = 0;
                data[1] = now + WINDOW_MILLIS;
            }

            if (data[0] < maxPerWindow) {
                data[0]++;
                return true;
            }

            return false;
        }
    }

    public boolean tryAcquire(String key, int maxPerWindow) {
        return tryAcquire(key, maxPerWindow, System.currentTimeMillis());
    }
}
