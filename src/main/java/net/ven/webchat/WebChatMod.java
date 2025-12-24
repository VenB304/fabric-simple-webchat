package net.ven.webchat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.ven.webchat.config.ModConfig;
import net.ven.webchat.bridge.ChatBridge;
import net.ven.webchat.web.WebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebChatMod implements ModInitializer {
    public static final String MOD_ID = "simple-webchat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Simple WebChat...");

        // 1. Load Config
        ModConfig.load();

        // 2. Register Lifecycle Events
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ChatBridge.setServer(server);
            WebServer.start();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            WebServer.stop();
            ChatBridge.setServer(null);
        });

        // 3. Register Chat Events (Listen to in-game chat)
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            // Get content
            String content = message.getSignedContent();
            String username = sender.getName().getString();

            // Broadcast to Web
            ChatBridge.broadcastToWeb(username, content);
        });

        // 4. Register Join/Leave for User List Updates
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
        // 5. Register Generic Commands
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT
                .register((dispatcher, registryAccess, environment) -> {
                    dispatcher.register(net.minecraft.server.command.CommandManager.literal("simplewebchat")
                            .requires(source -> source.hasPermissionLevel(2))
                            .then(net.minecraft.server.command.CommandManager.literal("reload")
                                    .executes(context -> {
                                        ModConfig.load();
                                        context.getSource().sendMessage(net.minecraft.text.Text
                                                .literal("§a[SimpleWebChat] Configuration reloaded!"));
                                        // Note: Some settings like Port require a restart.
                                        return 1;
                                    })));
                    // Alias /swc
                    dispatcher.register(net.minecraft.server.command.CommandManager.literal("swc")
                            .requires(source -> source.hasPermissionLevel(2))
                            .then(net.minecraft.server.command.CommandManager.literal("reload")
                                    .executes(context -> {
                                        ModConfig.load();
                                        context.getSource().sendMessage(net.minecraft.text.Text
                                                .literal("§a[SimpleWebChat] Configuration reloaded!"));
                                        return 1;
                                    })));
                });
    }
}
