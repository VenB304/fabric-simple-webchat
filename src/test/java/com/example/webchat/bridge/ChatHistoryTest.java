package com.example.webchat.bridge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatHistoryTest {

    @Test
    void tracksLinkedAndInGameIndependently() {
        ChatHistory history = new ChatHistory(Long.MAX_VALUE, 100);
        history.add("guest", "hi", 0L); // neither
        history.add("linkedWebUser", "hi", 1L, true); // linked web session
        history.add("aPlayer", "hi", 2L, false, true); // native in-game chat

        List<ChatHistory.Entry> snapshot = history.snapshot();
        assertFalse(snapshot.get(0).linked());
        assertFalse(snapshot.get(0).inGame());
        assertTrue(snapshot.get(1).linked());
        assertFalse(snapshot.get(1).inGame());
        assertFalse(snapshot.get(2).linked());
        assertTrue(snapshot.get(2).inGame());
    }

    @Test
    void trimsExcessEntriesByCount() {
        ChatHistory history = new ChatHistory(Long.MAX_VALUE, 3);
        long now = 0L;

        for (int i = 0; i < 5; i++) {
            history.add("user", "message " + i, now);
        }

        List<ChatHistory.Entry> snapshot = history.snapshot();
        assertEquals(3, snapshot.size());
        assertEquals("message 2", snapshot.get(0).message());
        assertEquals("message 4", snapshot.get(2).message());
    }

    @Test
    void trimsExpiredEntriesByAge() {
        ChatHistory history = new ChatHistory(1000, 100);

        history.add("user", "old", 0L);
        history.add("user", "new", 500L);

        // Advance past the old entry's expiry (0 + 1000) but not the new one's (500 + 1000)
        history.add("user", "trigger-cleanup", 1500L);

        List<ChatHistory.Entry> snapshot = history.snapshot();
        assertTrue(snapshot.stream().noneMatch(e -> e.message().equals("old")),
                "entries older than maxAgeMillis should be trimmed");
        assertTrue(snapshot.stream().anyMatch(e -> e.message().equals("new")));
    }

    @Test
    void snapshotPreservesInsertionOrder() {
        ChatHistory history = new ChatHistory(Long.MAX_VALUE, 100);
        history.add("a", "first", 0L);
        history.add("a", "second", 1L);
        history.add("a", "third", 2L);

        List<ChatHistory.Entry> snapshot = history.snapshot();
        assertEquals(List.of("first", "second", "third"),
                snapshot.stream().map(ChatHistory.Entry::message).toList());
    }
}
