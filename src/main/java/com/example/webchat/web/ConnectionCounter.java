package com.example.webchat.web;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks concurrent connections per key against a configurable cap. No
 * Minecraft/Fabric dependencies so it can be unit tested in isolation.
 */
public class ConnectionCounter {
    private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int max) {
        AtomicInteger counter = counts.computeIfAbsent(key, k -> new AtomicInteger(0));
        int updated = counter.incrementAndGet();
        if (updated > max) {
            counter.decrementAndGet();
            return false;
        }
        return true;
    }

    public void release(String key) {
        counts.computeIfPresent(key, (k, v) -> {
            int updated = v.decrementAndGet();
            return updated <= 0 ? null : v;
        });
    }

    public int current(String key) {
        AtomicInteger counter = counts.get(key);
        return counter == null ? 0 : counter.get();
    }
}
