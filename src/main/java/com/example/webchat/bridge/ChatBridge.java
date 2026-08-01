package com.example.webchat.bridge;

import com.example.webchat.config.ModConfig;
import io.javalin.websocket.WsContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.google.gson.Gson;

public class ChatBridge {
    private static final Gson GSON = new Gson();
    private static final ChatHistory history = new ChatHistory(
            ModConfig.getInstance().historyMaxAgeMinutes * 60 * 1000L,
            ModConfig.getInstance().historyMaxCount);

    private static MinecraftServer server;
    // Thread-safe set of active WebSocket sessions
    public static final Set<WsContext> activeSessions = ConcurrentHashMap.newKeySet();
    public static final Map<WsContext, String> sessionUsernames = new ConcurrentHashMap<>();
    public static final Map<WsContext, Boolean> sessionLinked = new ConcurrentHashMap<>();
    private static volatile Set<String> onlinePlayerNamesLower = Set.of();

    public static void setServer(MinecraftServer mcServer) {
        server = mcServer;
    }

    // Called from Web Thread (WebSocket)
    public static void sendToGame(String username, String message, boolean linked) {
        if (server == null)
            return;

        // Schedule on Main Server Thread
        server.execute(() -> {
            Component chatText = Component.literal("[WEB] ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(username).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(": " + message).withStyle(ChatFormatting.WHITE));

            // Broadcast to all players
            server.getPlayerList().broadcastSystemMessage(chatText, false);

            // Log to server console
            server.sendSystemMessage(chatText);

            // Also echo back to web users
            broadcastToWeb(username, message, linked);
        });
    }

    // Called from Game Thread (Mixin/Event) for "System" join/leave notifications - no
    // badge either way, since these aren't chat messages from a specific person.
    public static void broadcastToWeb(String sender, String message) {
        broadcastToWeb(sender, message, false, false);
    }

    // Called from Game Thread for a real in-game player's chat message: typing in
    // Minecraft is inherently verified (you can't fake being logged in as a player),
    // so this gets its own badge, distinct from a linked web session's checkmark.
    public static void broadcastGameMessage(String sender, String message) {
        broadcastToWeb(sender, message, false, true);
    }

    // Called from the Web Thread's echo-back after a web user's message reaches the game.
    public static void broadcastToWeb(String sender, String message, boolean linked) {
        broadcastToWeb(sender, message, linked, false);
    }

    private static void broadcastToWeb(String sender, String message, boolean linked, boolean inGame) {
        long now = System.currentTimeMillis();
        history.add(sender, message, now, linked, inGame);
        ChatLogger.log(sender, message);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "message");
        payload.put("user", sender);
        payload.put("message", message);
        payload.put("time", now);
        payload.put("linked", linked);
        payload.put("inGame", inGame);

        String jsonPayload = GSON.toJson(payload);

        for (WsContext ctx : activeSessions) {
            if (ctx.session.isOpen()) {
                ctx.send(jsonPayload);
            }
        }
    }

    public static void broadcastUserList() {
        if (server == null)
            return;

        server.execute(() -> {
            // Get In-Game Players (with UUID + skin texture, so the client can render head
            // icons). The "textures" GameProfile property is whatever the server actually
            // resolved for that player - Mojang's real texture for premium accounts, or
            // whatever a skin-restoring mod (e.g. SkinsRestorer) patched in for an
            // offline/cracked account - so this works correctly either way without us
            // needing any mod-specific integration.
            List<Map<String, Object>> players = new ArrayList<>();
            for (var player : server.getPlayerList().getPlayers()) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("name", player.getName().getString());
                entry.put("uuid", player.getUUID().toString());
                var textures = player.getGameProfile().properties().get("textures");
                if (!textures.isEmpty()) {
                    entry.put("skin", textures.iterator().next().value());
                }
                players.add(entry);
            }
            onlinePlayerNamesLower = players.stream()
                    .map(p -> ((String) p.get("name")).toLowerCase())
                    .collect(Collectors.toUnmodifiableSet());

            // Get Web Users (with their linked status, for the client to badge them)
            List<Map<String, Object>> webUsers = new ArrayList<>();
            for (Map.Entry<WsContext, String> entry : sessionUsernames.entrySet()) {
                Map<String, Object> webUser = new HashMap<>();
                webUser.put("name", entry.getValue());
                webUser.put("linked", Boolean.TRUE.equals(sessionLinked.get(entry.getKey())));
                webUsers.add(webUser);
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "playerList");
            payload.put("players", players);
            payload.put("webUsers", webUsers);

            String json = GSON.toJson(payload);

            for (WsContext ctx : activeSessions) {
                if (ctx.session.isOpen())
                    ctx.send(json);
            }
        });
    }

    public static void sendHistoryTo(WsContext ctx) {
        for (ChatHistory.Entry entry : history.snapshot()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "message");
            payload.put("user", entry.user());
            payload.put("message", entry.message());
            payload.put("time", entry.timestamp());
            payload.put("linked", entry.linked());
            payload.put("inGame", entry.inGame());
            ctx.send(GSON.toJson(payload));
        }
    }

    // Notify in-game about web joins
    public static void notifyWebJoin(String username) {
        if (server == null)
            return;
        server.execute(() -> {
            Component text = Component.literal("").append(Component.literal("[WEB] " + username + " joined the chat.")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
            server.getPlayerList().broadcastSystemMessage(text, false);
            broadcastToWeb("System", username + " joined the chat.");
            broadcastUserList(); // Update everyone's list
        });
    }

    public static void notifyWebQuit(String username) {
        if (server == null)
            return;
        server.execute(() -> {
            Component text = Component.literal("").append(Component.literal("[WEB] " + username + " left the chat.")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
            server.getPlayerList().broadcastSystemMessage(text, false);
            broadcastToWeb("System", username + " left the chat.");
            broadcastUserList(); // Update everyone's list
        });
    }

    /**
     * Forcibly disconnects a connected web user by username (case-insensitive).
     *
     * @return true if a matching session was found and kicked
     */
    public static boolean kickWebUser(String username) {
        for (Map.Entry<WsContext, String> entry : sessionUsernames.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(username)) {
                entry.getKey().closeSession(1008, "Kicked by admin");
                return true;
            }
        }
        return false;
    }

    /** Clears all per-session state for a closed websocket connection. */
    public static void removeSession(WsContext ctx) {
        activeSessions.remove(ctx);
        sessionUsernames.remove(ctx);
        sessionLinked.remove(ctx);
    }

    public static int getWebUserCount() {
        return sessionUsernames.size();
    }

    /** Best-effort check against the last known online-player list (refreshed on join/leave). */
    public static boolean isOnlinePlayerName(String name) {
        return onlinePlayerNamesLower.contains(name.toLowerCase());
    }
}
