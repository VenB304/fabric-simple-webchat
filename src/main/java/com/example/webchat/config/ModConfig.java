package com.example.webchat.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
    private static ModConfig instance;
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("web-chat-mod.json")
            .toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Config Fields
    public int webPort = 25585;
    public int rateLimitMessagesPerMinute = 20;
    public int maxMessageLength = 256;
    public boolean enableProfanityFilter = false;
    public String webPassword = ""; // Optional simple password for admin actions (not implemented yet)

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
