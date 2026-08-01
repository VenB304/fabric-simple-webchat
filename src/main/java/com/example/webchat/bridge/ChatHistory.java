package com.example.webchat.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory rolling chat history: capped by both age and count. No
 * Minecraft/Fabric dependencies so it can be unit tested in isolation.
 */
public class ChatHistory {
    public record Entry(long timestamp, String user, String message, boolean linked, boolean inGame) {
    }

    private final ConcurrentLinkedDeque<Entry> entries = new ConcurrentLinkedDeque<>();
    private final long maxAgeMillis;
    private final int maxCount;

    public ChatHistory(long maxAgeMillis, int maxCount) {
        this.maxAgeMillis = maxAgeMillis;
        this.maxCount = maxCount;
    }

    public void add(String user, String message, long now) {
        add(user, message, now, false, false);
    }

    public void add(String user, String message, long now, boolean linked) {
        add(user, message, now, linked, false);
    }

    public void add(String user, String message, long now, boolean linked, boolean inGame) {
        entries.add(new Entry(now, user, message, linked, inGame));

        long cutoff = now - maxAgeMillis;
        while (!entries.isEmpty() && entries.peek().timestamp() < cutoff) {
            entries.poll();
        }

        while (entries.size() > maxCount) {
            entries.poll();
        }
    }

    public List<Entry> snapshot() {
        return new ArrayList<>(entries);
    }
}
