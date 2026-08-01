package com.example.webchat.bridge;

import com.example.webchat.WebChatMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Appends every chat message to a plain-text log file on disk for moderation/audit
 * purposes, independent of the in-memory {@link ChatHistory} shown to new web clients.
 * Rotates the file once it exceeds {@link #MAX_SIZE_BYTES}.
 */
public final class ChatLogger {
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_INSTANT;

    private static final File LOG_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("web-chat-mod").resolve("chat.log").toFile();

    private ChatLogger() {
    }

    public static synchronized void log(String user, String message) {
        try {
            File parent = LOG_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                Files.createDirectories(parent.toPath());
            }
            rotateIfNeeded();

            String line = "[" + TIMESTAMP_FORMAT.format(Instant.now()) + "] " + user + ": " + message
                    + System.lineSeparator();
            try (FileWriter writer = new FileWriter(LOG_FILE, true)) {
                writer.write(line);
            }
        } catch (IOException e) {
            WebChatMod.LOGGER.error("Failed to write chat log", e);
        }
    }

    private static void rotateIfNeeded() throws IOException {
        if (LOG_FILE.exists() && LOG_FILE.length() > MAX_SIZE_BYTES) {
            Path rotated = LOG_FILE.toPath().resolveSibling("chat.log.1");
            Files.move(LOG_FILE.toPath(), rotated, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
