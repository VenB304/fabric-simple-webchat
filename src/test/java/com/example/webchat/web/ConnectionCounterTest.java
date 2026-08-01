package com.example.webchat.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionCounterTest {

    @Test
    void allowsUpToTheCap() {
        ConnectionCounter counter = new ConnectionCounter();

        assertTrue(counter.tryAcquire("1.2.3.4", 3));
        assertTrue(counter.tryAcquire("1.2.3.4", 3));
        assertTrue(counter.tryAcquire("1.2.3.4", 3));
        assertFalse(counter.tryAcquire("1.2.3.4", 3), "4th connection should be rejected at cap 3");
        assertEquals(3, counter.current("1.2.3.4"));
    }

    @Test
    void releasingFreesUpASlot() {
        ConnectionCounter counter = new ConnectionCounter();

        counter.tryAcquire("1.2.3.4", 1);
        assertFalse(counter.tryAcquire("1.2.3.4", 1));

        counter.release("1.2.3.4");
        assertTrue(counter.tryAcquire("1.2.3.4", 1), "releasing should free capacity for a new connection");
    }

    @Test
    void releaseNeverGoesNegative() {
        ConnectionCounter counter = new ConnectionCounter();

        counter.release("1.2.3.4");
        counter.release("1.2.3.4");

        assertEquals(0, counter.current("1.2.3.4"));
        assertTrue(counter.tryAcquire("1.2.3.4", 1));
    }
}
