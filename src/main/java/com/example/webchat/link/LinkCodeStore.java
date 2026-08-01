package com.example.webchat.link;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates and redeems short-lived, human-typeable link codes tied to an in-game
 * account. No Minecraft/Fabric or file I/O dependencies so it can be unit tested in
 * isolation; {@link LinkManager} wraps this with persistence for the resulting tokens.
 */
public class LinkCodeStore {
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no O/0, I/1
    private static final int CODE_LENGTH = 6;

    private final long codeTtlMillis;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingCode> pendingCodes = new ConcurrentHashMap<>();

    public record PendingCode(String uuid, String username, long expiresAt) {
    }

    public LinkCodeStore(long codeTtlMillis) {
        this.codeTtlMillis = codeTtlMillis;
    }

    /** Generates a new code for the given player, invalidating any of their existing codes. */
    public String generateCode(String uuid, String username, long now) {
        pendingCodes.values().removeIf(p -> p.expiresAt() < now);
        pendingCodes.entrySet().removeIf(e -> e.getValue().uuid().equals(uuid));

        String code;
        do {
            code = randomCode();
        } while (pendingCodes.putIfAbsent(code, new PendingCode(uuid, username, now + codeTtlMillis)) != null);

        return code;
    }

    /** Redeems (and invalidates) a code if it exists and hasn't expired. */
    public Optional<PendingCode> redeem(String code, long now) {
        PendingCode pending = pendingCodes.remove(code.toUpperCase());
        if (pending == null || pending.expiresAt() < now) {
            return Optional.empty();
        }
        return Optional.of(pending);
    }

    public int pendingCount() {
        return pendingCodes.size();
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
