package com.example.webchat.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void allowsUpToTheConfiguredLimit() {
        RateLimiter limiter = new RateLimiter();
        long now = 0L;

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("1.2.3.4", 5, now), "message " + i + " should be allowed");
        }
        assertFalse(limiter.tryAcquire("1.2.3.4", 5, now), "6th message within the window should be rejected");
    }

    @Test
    void windowResetsAfterOneMinute() {
        RateLimiter limiter = new RateLimiter();
        long now = 0L;

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("1.2.3.4", 5, now));
        }
        assertFalse(limiter.tryAcquire("1.2.3.4", 5, now));

        long later = now + 60_001;
        assertTrue(limiter.tryAcquire("1.2.3.4", 5, later), "a new window should allow messages again");
    }

    @Test
    void limitsAreIndependentPerKey() {
        RateLimiter limiter = new RateLimiter();
        long now = 0L;

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("1.2.3.4", 5, now));
        }
        assertTrue(limiter.tryAcquire("5.6.7.8", 5, now), "a different key should have its own budget");
    }
}
