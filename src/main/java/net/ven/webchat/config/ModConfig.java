package net.ven.webchat.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
    private static volatile ModConfig instance;
    public static final java.nio.file.Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir()
            .resolve("simple-webchat");
    // Config file location
    private static final File CONFIG_FILE = CONFIG_DIR.resolve("web-chat-mod.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // --- Server Settings ---
    public int webPort = 25595;
    public boolean trustProxy = true; // Set to true if running behind a reverse proxy (e.g. Nginx, Cloudflare)

    // --- Authentication ---
    public enum AuthMode {
        NONE, SIMPLE, LINKED
    }

    public AuthMode authMode = AuthMode.NONE; // Options: NONE, SIMPLE, LINKED
    public String webPassword = ""; // Required if AuthMode is SIMPLE
    public int otpRateLimitSeconds = 30; // Seconds between OTP requests (LINKED mode)

    // --- Security (SSL) ---
    public boolean enableSSL = false;
    public String sslKeyStorePath = ""; // Path to PKCS12 or JKS keystore
    public String sslKeyStorePassword = "";

    // --- Chat Limits ---
    public int maxMessageLength = 256;
    public int maxHistoryMessages = 50; // Number of messages to keep in memory
    public int messageRetentionMinutes = 30; // Max age of messages in memory
    public int rateLimitMessagesPerMinute = 20;

    // --- Customization ---
    public String favicon = "favicon.ico"; // URL or /custom/file.ico
    public String defaultSound = "ding.mp3";
    public java.util.List<String> soundPresets = new java.util.ArrayList<>();

    // --- Moderation ---
    public boolean enableProfanityFilter = false;
    public java.util.List<String> profanityList = java.util.Arrays.asList("exampleBadWord1", "exampleBadword2");

    public static ModConfig getInstance() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        // Ensure directory exists
        if (!CONFIG_DIR.toFile().exists()) {
            CONFIG_DIR.toFile().mkdirs();
        }

        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                instance = GSON.fromJson(reader, ModConfig.class);
                // Ensure defaults if missing (e.g. if loaded from old config)
                // Ensure defaults if missing
                if (instance.profanityList == null) {
                    instance.profanityList = java.util.Arrays.asList("exampleBadWord1", "exampleBadWord2");
                }
                if (instance.soundPresets == null) {
                    instance.soundPresets = new java.util.ArrayList<>();
                }
                // Add default standard sound if list is empty, just to have something
                if (instance.soundPresets.isEmpty()) {
                    instance.soundPresets.add("ding.mp3");
                }
            } catch (IOException e) {
                e.printStackTrace();
                // Fallback to default
                instance = new ModConfig();
            }
        } else {
            instance = new ModConfig();
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void reset() {
        instance = new ModConfig();
        save();
    }
}
