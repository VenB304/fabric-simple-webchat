package net.ven.webchat.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class AuthManager {
    private static final java.nio.file.Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir()
            .resolve("simple-webchat");
    private static final File SESSION_FILE = CONFIG_DIR.resolve("sessions.json").toFile();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Token -> Session
    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();

    // Player UUID -> OTP Code
    private static final Map<UUID, String> otpCache = new ConcurrentHashMap<>();

    // IP -> Last Request Time
    private static final Map<String, Long> otpRateLimit = new ConcurrentHashMap<>();

    // Executor for async IO
    private static final java.util.concurrent.ExecutorService ioExecutor = java.util.concurrent.Executors
            .newSingleThreadExecutor();

    static {
        loadSessions();
    }

    public static String generateOTP(UUID playerUuid) {
        // Generate 6-digit code
        int code = ThreadLocalRandom.current().nextInt(100000, 999999);
        String codeStr = String.valueOf(code);
        otpCache.put(playerUuid, codeStr);
        return codeStr;
    }

    public static boolean verifyOTP(UUID playerUuid, String code) {
        String stored = otpCache.get(playerUuid);
        if (stored != null && stored.equals(code)) {
            otpCache.remove(playerUuid);
            return true;
        }
        return false;
    }

    public static boolean canRequestOtp(String ip) {
        long now = System.currentTimeMillis();
        long last = otpRateLimit.getOrDefault(ip, 0L);
        long limit = net.ven.webchat.config.ModConfig.getInstance().otpRateLimitSeconds * 1000L;

        if (now - last < limit) {
            return false;
        }
        otpRateLimit.put(ip, now);
        return true;
    }

    public static long getOtpResetTime(String ip) {
        long last = otpRateLimit.getOrDefault(ip, 0L);
        long limit = net.ven.webchat.config.ModConfig.getInstance().otpRateLimitSeconds * 1000L;
        return last + limit;
    }

    public static String createSession(UUID playerUuid, String username) {
        String token = UUID.randomUUID().toString();
        // Expires in 30 days
        long expiry = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000);
        Session session = new Session(playerUuid, username, expiry);

        sessions.put(token, session);
        saveSessions();
        return token;
    }

    public static Session verifySession(String token) {
        if (token == null || token.isEmpty())
            return null;
        Session session = sessions.get(token);
        if (session != null) {
            if (System.currentTimeMillis() > session.expiry) {
                sessions.remove(token);
                saveSessions();
                return null;
            }
            return session;
        }
        return null;
    }

    private static void loadSessions() {
        if (!CONFIG_DIR.toFile().exists()) {
            CONFIG_DIR.toFile().mkdirs();
        }

        if (SESSION_FILE.exists()) {
            try {
                Map<String, Session> loaded = MAPPER.readValue(SESSION_FILE,
                        new TypeReference<ConcurrentHashMap<String, Session>>() {
                        });
                if (loaded != null) {
                    sessions.putAll(loaded);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void saveSessions() {
        // Run in background to avoid blocking main thread or web server thread
        ioExecutor.submit(() -> {
            try {
                MAPPER.writeValue(SESSION_FILE, sessions);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public static void cleanup() {
        long now = System.currentTimeMillis();
        boolean changed = false;

        // Cleanup Sessions
        // RemoveIf returns true if any elements were removed
        changed = sessions.entrySet().removeIf(entry -> now > entry.getValue().expiry);

        // Cleanup OTP Rate Limits
        long limit = net.ven.webchat.config.ModConfig.getInstance().otpRateLimitSeconds * 1000L;
        otpRateLimit.entrySet().removeIf(entry -> now - entry.getValue() > limit);

        // Cleanup OTP Codes (Optional: remove codes older than 5 mins?
        // Currently OTPs don't have explicit timestamps in the map, just UUID->Code.
        // We rely on overwrite. But good to clear eventually or add timestamp wrapper.
        // For now, we leave OTPs as they are small strings. Session file is the biggest
        // concern.)

        if (changed) {
            saveSessions();
        }
    }

    // Default constructor needed for Jackson deserialization if no creators are
    // found
    public static class Session {
        public UUID uuid;
        public String username;
        public long expiry;

        public Session() {
        } // Jackson needs this

        public Session(UUID uuid, String username, long expiry) {
            this.uuid = uuid;
            this.username = username;
            this.expiry = expiry;
        }
    }
}
