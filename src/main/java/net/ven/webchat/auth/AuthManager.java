package net.ven.webchat.auth;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class AuthManager {
    private static final File SESSION_FILE = FabricLoader.getInstance().getConfigDir().resolve("web-chat-sessions.json")
            .toFile();
    private static final Gson GSON = new Gson();

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
        if (SESSION_FILE.exists()) {
            try (FileReader reader = new FileReader(SESSION_FILE)) {
                Type type = new TypeToken<ConcurrentHashMap<String, Session>>() {
                }.getType();
                Map<String, Session> loaded = GSON.fromJson(reader, type);
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
            try (FileWriter writer = new FileWriter(SESSION_FILE)) {
                GSON.toJson(sessions, writer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public static class Session {
        public UUID uuid;
        public String username;
        public long expiry;

        public Session(UUID uuid, String username, long expiry) {
            this.uuid = uuid;
            this.username = username;
            this.expiry = expiry;
        }
    }
}
