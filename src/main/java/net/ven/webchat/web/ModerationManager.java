package net.ven.webchat.web;

import net.ven.webchat.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ModerationManager {
    private static final File BAN_FILE = FabricLoader.getInstance().getConfigDir().resolve("web-chat-bans.json")
            .toFile();
    private static final Gson GSON = new Gson();
    private static Set<String> bannedIps = Collections.synchronizedSet(new HashSet<>());

    // IP -> Timestamp of last message windows
    // Simple Token Bucket-ish: Allow N messages per minute.
    // We track: IP -> [msg_count, reset_timestamp]
    private static final Map<String, long[]> rateLimits = new ConcurrentHashMap<>();

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
        long now = System.currentTimeMillis();
        int maxPerMin = ModConfig.getInstance().rateLimitMessagesPerMinute;

        // Lazy cleanup: 1% chance to run cleanup
        if (Math.random() < 0.01) {
            rateLimits.entrySet().removeIf(entry -> entry.getValue()[1] < now);
        }

        long[] data = rateLimits.computeIfAbsent(ip, k -> new long[] { 0, now + 60000 });

        // Check if window expired
        if (now > data[1]) {
            // New window
            data[0] = 0;
            data[1] = now + 60000;
        }

        if (data[0] < maxPerMin) {
            data[0]++;
            return true;
        }

        return false;
    }

    public static long getRateLimitReset(String ip) {
        long[] data = rateLimits.get(ip);
        if (data == null)
            return 0;
        return data[1];
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
                e.printStackTrace();
            }
        }
    }

    private static void saveBans() {
        try (FileWriter writer = new FileWriter(BAN_FILE)) {
            GSON.toJson(bannedIps, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
