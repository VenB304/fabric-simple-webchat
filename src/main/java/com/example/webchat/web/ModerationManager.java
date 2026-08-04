package com.example.webchat.web;

import com.example.webchat.WebChatMod;
import com.example.webchat.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ModerationManager {
    private static final File BAN_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("web-chat-mod").resolve("bans.json").toFile();
    private static final Gson GSON = new Gson();
    private static Set<String> bannedIps = Collections.synchronizedSet(new HashSet<>());

    private static final RateLimiter MESSAGE_RATE_LIMITER = new RateLimiter();
    private static final RateLimiter LINK_RATE_LIMITER = new RateLimiter();
    private static final ConnectionCounter CONNECTION_COUNTER = new ConnectionCounter();
    private static final ProfanityFilter PROFANITY_FILTER = new ProfanityFilter();

    static {
        loadBans();
    }

    public static boolean isBanned(String ip) {
        return bannedIps.contains(ip);
    }

    public static void ban(String ip) {
        bannedIps.add(ip);
        saveBans();
    }

    public static void unban(String ip) {
        bannedIps.remove(ip);
        saveBans();
    }

    /**
     * Checks if the user is rate limited.
     *
     * @param ip The user's IP
     * @return true if allowed, false if rate limited
     */
    public static boolean checkRateLimit(String ip) {
        return MESSAGE_RATE_LIMITER.tryAcquire(ip, ModConfig.getInstance().rateLimitMessagesPerMinute);
    }

    /**
     * Separate, stricter limiter for LINK/UNLINK attempts, so they can't be used to
     * brute-force a valid link code by hammering guesses faster than normal chat spam
     * would be caught by {@link #checkRateLimit}.
     */
    public static boolean checkLinkRateLimit(String ip) {
        return LINK_RATE_LIMITER.tryAcquire(ip, ModConfig.getInstance().linkAttemptsPerMinute);
    }

    /**
     * Registers a new websocket connection from the given IP, enforcing the configured
     * per-IP concurrent-connection cap. Call {@link #releaseConnection(String)} when the
     * connection closes.
     *
     * @return true if the connection is allowed, false if the IP is already at its cap
     */
    public static boolean tryAcquireConnection(String ip) {
        return CONNECTION_COUNTER.tryAcquire(ip, ModConfig.getInstance().maxConnectionsPerIp);
    }

    public static void releaseConnection(String ip) {
        CONNECTION_COUNTER.release(ip);
    }

    /**
     * Replaces configured profanity words with asterisks. No-op if the filter is disabled
     * or no words are configured.
     */
    public static String filterProfanity(String message) {
        ModConfig config = ModConfig.getInstance();
        if (!config.enableProfanityFilter) {
            return message;
        }
        return PROFANITY_FILTER.filter(message, config.profanityWords);
    }

    private static void loadBans() {
        if (BAN_FILE.exists()) {
            try (FileReader reader = new FileReader(BAN_FILE)) {
                Type setType = new TypeToken<HashSet<String>>() {
                }.getType();
                Set<String> loaded = GSON.fromJson(reader, setType);
                if (loaded != null)
                    bannedIps.addAll(loaded);
            } catch (IOException e) {
                WebChatMod.LOGGER.error("Failed to read {}", BAN_FILE.getName(), e);
            }
        }
    }

    private static void saveBans() {
        try {
            Files.createDirectories(BAN_FILE.getParentFile().toPath());
            try (FileWriter writer = new FileWriter(BAN_FILE)) {
                // Collections.synchronizedSet requires manual synchronization for iteration
                // (which toJson does internally) - a concurrent ban()/unban() during a save
                // would otherwise risk a ConcurrentModificationException.
                synchronized (bannedIps) {
                    GSON.toJson(bannedIps, writer);
                }
            }
        } catch (IOException e) {
            WebChatMod.LOGGER.error("Failed to write {}", BAN_FILE.getName(), e);
        }
    }
}
