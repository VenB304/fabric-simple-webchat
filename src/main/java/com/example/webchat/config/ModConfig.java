package com.example.webchat.config;

import com.example.webchat.WebChatMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class ModConfig {
    private static ModConfig instance;
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("web-chat-mod").resolve("config.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Config Fields
    public int webPort = 25585;
    public int rateLimitMessagesPerMinute = 20;
    public int maxMessageLength = 256;
    public int maxConnectionsPerIp = 5;
    public boolean enableProfanityFilter = false;
    public List<String> profanityWords = new ArrayList<>();
    public int historyMaxAgeMinutes = 30;
    public int historyMaxCount = 50;

    // If true, unlinked (guest) web users can view chat but not send messages until
    // they run /webchat link in-game and redeem the code on the web page.
    public boolean requireLinkToChat = false;
    public int linkAttemptsPerMinute = 10;

    // How long a persistent link token stays valid, in days. 0 means it never expires.
    public int linkTokenExpiryDays = 0;

    // If non-empty, only websocket handshakes whose Origin header exactly matches one of
    // these values are accepted (e.g. "https://chat.venb.top"). Empty means unrestricted.
    public List<String> allowedOrigins = new ArrayList<>();

    // TLS / WSS (optional - lets the server run HTTPS/WSS directly without a reverse proxy)
    public boolean enableTls = false;
    public String tlsKeystorePath = "";
    public String tlsKeystorePassword = "";

    // UI customization, applied client-side via GET /webchat-config.json
    public String webChatTitle = "Minecraft Web Chat";
    public String webChatHeader = "Web Chat";
    public String defaultSound = "ding.mp3";

    // If non-empty, web users must supply this exact password (?password=) to connect
    // at all, on top of (and independent from) account linking. Empty disables it.
    public String webPassword = "";

    public static ModConfig getInstance() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                instance = GSON.fromJson(reader, ModConfig.class);
                if (instance == null) {
                    instance = new ModConfig();
                }
            } catch (IOException e) {
                WebChatMod.LOGGER.error("Failed to read {}, falling back to defaults", CONFIG_FILE.getName(), e);
                instance = new ModConfig();
            }
        } else {
            instance = new ModConfig();
        }
        instance.validate();
        // Always rewrite: backfills newly-added fields and persists any corrections
        // validate() just made, so the file on disk never silently drifts from the
        // schema this build actually reads.
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParentFile().toPath());
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            WebChatMod.LOGGER.error("Failed to write {}", CONFIG_FILE.getName(), e);
        }
    }

    /** Clamps out-of-range values to safe defaults instead of trusting a hand-edited file. */
    private void validate() {
        if (webPort < 1 || webPort > 65535) {
            WebChatMod.LOGGER.warn("Invalid webPort {} in config, falling back to 25585", webPort);
            webPort = 25585;
        }
        if (rateLimitMessagesPerMinute <= 0) {
            WebChatMod.LOGGER.warn("Invalid rateLimitMessagesPerMinute {} in config, falling back to 20",
                    rateLimitMessagesPerMinute);
            rateLimitMessagesPerMinute = 20;
        }
        if (maxMessageLength <= 0) {
            WebChatMod.LOGGER.warn("Invalid maxMessageLength {} in config, falling back to 256", maxMessageLength);
            maxMessageLength = 256;
        }
        if (maxConnectionsPerIp <= 0) {
            WebChatMod.LOGGER.warn("Invalid maxConnectionsPerIp {} in config, falling back to 5",
                    maxConnectionsPerIp);
            maxConnectionsPerIp = 5;
        }
        if (profanityWords == null) {
            profanityWords = new ArrayList<>();
        }
        if (allowedOrigins == null) {
            allowedOrigins = new ArrayList<>();
        }
        if (historyMaxAgeMinutes <= 0) {
            WebChatMod.LOGGER.warn("Invalid historyMaxAgeMinutes {} in config, falling back to 30",
                    historyMaxAgeMinutes);
            historyMaxAgeMinutes = 30;
        }
        if (historyMaxCount <= 0) {
            WebChatMod.LOGGER.warn("Invalid historyMaxCount {} in config, falling back to 50", historyMaxCount);
            historyMaxCount = 50;
        }
        if (linkAttemptsPerMinute <= 0) {
            WebChatMod.LOGGER.warn("Invalid linkAttemptsPerMinute {} in config, falling back to 10",
                    linkAttemptsPerMinute);
            linkAttemptsPerMinute = 10;
        }
        if (linkTokenExpiryDays < 0) {
            WebChatMod.LOGGER.warn("Invalid linkTokenExpiryDays {} in config, falling back to 0 (never expire)",
                    linkTokenExpiryDays);
            linkTokenExpiryDays = 0;
        }
        if (enableTls && (tlsKeystorePath == null || tlsKeystorePath.isBlank())) {
            WebChatMod.LOGGER.warn("enableTls is true but tlsKeystorePath is empty; disabling TLS");
            enableTls = false;
        }
        if (webChatTitle == null || webChatTitle.isBlank()) {
            webChatTitle = "Minecraft Web Chat";
        }
        if (webChatHeader == null || webChatHeader.isBlank()) {
            webChatHeader = "Web Chat";
        }
        if (defaultSound == null || defaultSound.isBlank()) {
            defaultSound = "ding.mp3";
        }
        if (webPassword == null) {
            webPassword = "";
        }
    }
}
