package com.example.webchat.link;

import com.example.webchat.WebChatMod;
import com.example.webchat.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Links a web session to a verified in-game account: a player runs {@code /webchat link}
 * to get a short-lived code, and redeeming that code on the web side issues a long-lived
 * persistent token the browser can store and reuse on future visits.
 */
public class LinkManager {
    private static final long CODE_TTL_MILLIS = 5 * 60 * 1000L;
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    private static final File LINKS_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("web-chat-mod").resolve("links.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final LinkCodeStore CODE_STORE = new LinkCodeStore(CODE_TTL_MILLIS);

    private record TokenEntry(String uuid, String username, long issuedAt) {
    }

    private static Map<String, TokenEntry> tokens = new ConcurrentHashMap<>();

    static {
        load();
    }

    /** Generates a new short-lived code for the given player, replacing any existing one. */
    public static String generateCode(String uuid, String username) {
        return CODE_STORE.generateCode(uuid, username, System.currentTimeMillis());
    }

    /** Redeems a code, returning a fresh persistent token paired with the linked account. */
    public static Optional<LinkResult> redeemCode(String code) {
        return CODE_STORE.redeem(code, System.currentTimeMillis()).map(pending -> {
            String token = UUID.randomUUID().toString();
            tokens.put(token, new TokenEntry(pending.uuid(), pending.username(), System.currentTimeMillis()));
            save();
            return new LinkResult(token, new LinkedAccount(pending.uuid(), pending.username()));
        });
    }

    public static Optional<LinkedAccount> lookupToken(String token) {
        if (token == null || token.isBlank())
            return Optional.empty();

        TokenEntry entry = tokens.get(token);
        if (entry == null) {
            return Optional.empty();
        }

        int expiryDays = ModConfig.getInstance().linkTokenExpiryDays;
        if (expiryDays > 0 && System.currentTimeMillis() - entry.issuedAt() > expiryDays * DAY_MILLIS) {
            tokens.remove(token);
            save();
            return Optional.empty();
        }

        return Optional.of(new LinkedAccount(entry.uuid(), entry.username()));
    }

    /**
     * Removes every token past {@code linkTokenExpiryDays}. Unlike {@link #lookupToken}'s
     * lazy per-token expiry check, this catches tokens that expired but were never looked
     * up again, which would otherwise sit in memory and links.json forever.
     *
     * @return the number of tokens removed
     */
    public static int pruneExpiredTokens() {
        int expiryDays = ModConfig.getInstance().linkTokenExpiryDays;
        if (expiryDays <= 0) {
            return 0;
        }
        long cutoff = System.currentTimeMillis() - expiryDays * DAY_MILLIS;
        int before = tokens.size();
        tokens.values().removeIf(entry -> entry.issuedAt() < cutoff);
        int removed = before - tokens.size();
        if (removed > 0) {
            save();
        }
        return removed;
    }

    public static void revokeToken(String token) {
        if (token != null && tokens.remove(token) != null) {
            save();
        }
    }

    /** Revokes every token linked to the given in-game username (case-insensitive). */
    public static int revokeByUsername(String username) {
        int removed = 0;
        var it = tokens.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().username().equalsIgnoreCase(username)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            save();
        }
        return removed;
    }

    private static void load() {
        if (LINKS_FILE.exists()) {
            try (FileReader reader = new FileReader(LINKS_FILE)) {
                Type type = new TypeToken<Map<String, TokenEntry>>() {
                }.getType();
                Map<String, TokenEntry> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    tokens = new ConcurrentHashMap<>(loaded);
                }
            } catch (IOException e) {
                WebChatMod.LOGGER.error("Failed to read {}", LINKS_FILE.getName(), e);
            }
        }
    }

    private static void save() {
        try {
            Files.createDirectories(LINKS_FILE.getParentFile().toPath());
            try (FileWriter writer = new FileWriter(LINKS_FILE)) {
                GSON.toJson(tokens, writer);
            }
        } catch (IOException e) {
            WebChatMod.LOGGER.error("Failed to write {}", LINKS_FILE.getName(), e);
        }
    }
}
