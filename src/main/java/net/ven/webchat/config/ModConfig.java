package net.ven.webchat.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ModConfig {
    private static volatile ModConfig instance;
    public static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir()
            .resolve("simple-webchat");
    // Config file location
    private static final File CONFIG_FILE = CONFIG_DIR.resolve("web-chat-mod.yaml").toFile();
    private static final File OLD_JSON_FILE = CONFIG_DIR.resolve("web-chat-mod.json").toFile();

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

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
    public List<String> soundPresets = new ArrayList<>(Arrays.asList("ding.mp3"));

    // --- Moderation ---
    public boolean enableProfanityFilter = false;
    public List<String> profanityList = new ArrayList<>(Arrays.asList("exampleBadWord1", "exampleBadword2"));

    // Default configuration with comments
    private static final String DEFAULT_CONFIG_CONTENT = """
            # Fabric Simple WebChat Configuration
            # You can rearrange these options and add comments.

            # --- Server Settings ---
            # The port the web server will listen on.
            webPort: 25595
            # Set to true if running behind a reverse proxy (e.g. Nginx, Cloudflare) to correctly identify client IPs.
            trustProxy: true

            # --- Authentication ---
            # Options: NONE, SIMPLE, LINKED
            # NONE: No authentication required.
            # SIMPLE: Single password for all users.
            # LINKED: Users must link their Minecraft account via OTP.
            authMode: NONE

            # Required if AuthMode is SIMPLE.
            webPassword: ""

            # Seconds between OTP requests (LINKED mode).
            otpRateLimitSeconds: 30

            # --- Security (SSL) ---
            # Enable built-in SSL support (not recommended if using a reverse proxy).
            enableSSL: false
            # Path to PKCS12 or JKS keystore.
            sslKeyStorePath: ""
            # Password for the keystore.
            sslKeyStorePassword: ""

            # --- Chat Limits ---
            maxMessageLength: 256
            # Number of messages to keep in memory for history implementation.
            maxHistoryMessages: 50
            # Max age of messages in memory (minutes).
            messageRetentionMinutes: 30
            rateLimitMessagesPerMinute: 20

            # --- Customization ---
            # URL to an icon or path (relative to config dir or absolute)
            favicon: "favicon.ico"
            # Default sound file name
            defaultSound: "ding.mp3"
            # List of available sounds in the sidebar
            soundPresets:
              - "ding.mp3"

            # --- Moderation ---
            enableProfanityFilter: false
            # List of words to filter
            profanityList:
              - "exampleBadWord1"
              - "exampleBadword2"
            """;

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
            try {
                instance = MAPPER.readValue(CONFIG_FILE, ModConfig.class);
                ensureDefaults();
            } catch (IOException e) {
                e.printStackTrace();
                // Fallback to default
                instance = new ModConfig();
            }
        } else if (OLD_JSON_FILE.exists()) {
            // Migration logic: Backup JSON and create new YAML
            try {
                Files.move(OLD_JSON_FILE.toPath(), OLD_JSON_FILE.toPath().resolveSibling("web-chat-mod.json.old"),
                        StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Migrated old config to .old");
            } catch (IOException e) {
                e.printStackTrace();
            }
            instance = new ModConfig();
            writeDefault();
        } else {
            instance = new ModConfig();
            writeDefault();
        }
    }

    private static void ensureDefaults() {
        if (instance.profanityList == null) {
            instance.profanityList = new ArrayList<>(Arrays.asList("exampleBadWord1", "exampleBadword2"));
        }
        if (instance.soundPresets == null) {
            instance.soundPresets = new ArrayList<>();
        }
        if (instance.soundPresets.isEmpty()) {
            instance.soundPresets.add("ding.mp3");
        }
    }

    private static void writeDefault() {
        try {
            Files.writeString(CONFIG_FILE.toPath(), DEFAULT_CONFIG_CONTENT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        // Warning: This overwrites the file. If used programmatically, comments will be
        // lost.
        // Currently only used for initialization/reset.
        writeDefault();
    }

    public static void reset() {
        instance = new ModConfig();
        save();
    }
}
