package com.example.webchat.link;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkCodeStoreTest {

    @Test
    void redeemsAFreshlyGeneratedCode() {
        LinkCodeStore store = new LinkCodeStore(60_000);
        String code = store.generateCode("uuid-1", "Steve", 0L);

        Optional<LinkCodeStore.PendingCode> result = store.redeem(code, 1000L);

        assertTrue(result.isPresent());
        assertEquals("uuid-1", result.get().uuid());
        assertEquals("Steve", result.get().username());
    }

    @Test
    void codeIsSingleUse() {
        LinkCodeStore store = new LinkCodeStore(60_000);
        String code = store.generateCode("uuid-1", "Steve", 0L);

        assertTrue(store.redeem(code, 1000L).isPresent());
        assertTrue(store.redeem(code, 1000L).isEmpty(), "redeeming the same code twice should fail");
    }

    @Test
    void expiredCodeCannotBeRedeemed() {
        LinkCodeStore store = new LinkCodeStore(1000);
        String code = store.generateCode("uuid-1", "Steve", 0L);

        assertTrue(store.redeem(code, 1001L).isEmpty(), "code should be expired one ms after its TTL");
    }

    @Test
    void unknownCodeIsRejected() {
        LinkCodeStore store = new LinkCodeStore(60_000);
        assertTrue(store.redeem("NOPE12", 0L).isEmpty());
    }

    @Test
    void generatingANewCodeInvalidatesThePreviousOneForThatPlayer() {
        LinkCodeStore store = new LinkCodeStore(60_000);
        String first = store.generateCode("uuid-1", "Steve", 0L);
        String second = store.generateCode("uuid-1", "Steve", 100L);

        assertTrue(store.redeem(first, 200L).isEmpty(), "old code should be invalidated by regenerating");
        assertTrue(store.redeem(second, 200L).isPresent());
    }

    @Test
    void redeemIsCaseInsensitive() {
        LinkCodeStore store = new LinkCodeStore(60_000);
        String code = store.generateCode("uuid-1", "Steve", 0L);

        assertTrue(store.redeem(code.toLowerCase(), 1000L).isPresent());
    }
}
