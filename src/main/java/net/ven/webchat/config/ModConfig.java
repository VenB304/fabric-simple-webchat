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
    private static final java.nio.file.Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir()
            .resolve("simple-webchat");
    // Config file location
    private static final File CONFIG_FILE = CONFIG_DIR.resolve("web-chat-mod.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Config Fields
    public int webPort = 25595;
    public int rateLimitMessagesPerMinute = 20;
    public int maxMessageLength = 256;
    public boolean enableProfanityFilter = false;
    public String webPassword = ""; // Optional simple password for admin actions (not implemented yet)

    public enum AuthMode {
        NONE, SIMPLE, LINKED
    }

    public AuthMode authMode = AuthMode.NONE;

    // Security Features
    public boolean enableSSL = false;
    public String sslKeyStorePath = "";
    public String sslKeyStorePassword = "";
    public boolean trustProxy = true;
    public int otpRateLimitSeconds = 30;

    // Message History
    public int maxHistoryMessages = 50; // 0 = infinite
    public int messageRetentionMinutes = 30; // 0 = infinite

    // Moderation
    public java.util.List<String> profanityList = java.util.Arrays.asList("badword", "naughty");

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
                if (instance.profanityList == null) {
                    instance.profanityList = java.util.Arrays.asList("badword", "naughty");
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
}
