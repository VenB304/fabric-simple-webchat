package com.example.webchat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import com.example.webchat.config.ModConfig;
import com.example.webchat.bridge.ChatBridge;
import com.example.webchat.command.WebChatCommand;
import com.example.webchat.link.LinkManager;
import com.example.webchat.web.WebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebChatMod implements ModInitializer {
    public static final String MOD_ID = "web-chat-mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Web Chat Mod...");

        // 1. Load Config
        ModConfig.load();
        LinkManager.pruneExpiredTokens();

        // 2. Register Commands
        WebChatCommand.register();

        // 3. Register Lifecycle Events
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ChatBridge.setServer(server);
            WebServer.start();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            WebServer.stop();
            ChatBridge.setServer(null);
        });

        // Seed the online-player cache immediately so the first web connection can
        // already check for username collisions, rather than waiting for a join/leave.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> ChatBridge.broadcastUserList());

        // 4. Register Chat Events (Listen to in-game chat)
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            // Get content
            String content = message.signedContent();
            String username = sender.getName().getString();

            // Broadcast to Web
            ChatBridge.broadcastGameMessage(username, content);
        });

        // 5. Register Join/Leave for User List Updates
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            String playerName = handler.getPlayer().getName().getString();

            // Notify Web Users
            ChatBridge.broadcastToWeb("System", playerName + " joined the server.");

            // Delay list update slightly to ensure player list is 100% updated in all
            // contexts
            // Although ServerPlayConnectionEvents.JOIN is post-join, let's be safe.
            // We can just execute it.
            server.execute(() -> ChatBridge.broadcastUserList());
        });

        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            String playerName = handler.getPlayer().getName().getString();

            // Notify Web Users
            ChatBridge.broadcastToWeb("System", playerName + " left the server.");

            server.execute(() -> ChatBridge.broadcastUserList());
        });
    }
}
