package net.ven.webchat.bridge;

import io.javalin.websocket.WsContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.Gson;

public class ChatBridge {
    private static MinecraftServer server;
    // Thread-safe set of active WebSocket sessions
    public static final Set<WsContext> activeSessions = ConcurrentHashMap.newKeySet();
    public static final Map<WsContext, String> sessionUsernames = new ConcurrentHashMap<>();

    private static final Gson GSON = new Gson();

    public static void setServer(MinecraftServer mcServer) {
        server = mcServer;
    }

    public static MinecraftServer getServer() {
        return server;
    }

    // Called from Web Thread (WebSocket)
    public static void sendToGame(String username, String message) {
        if (server == null)
            return;

        // Schedule on Main Server Thread
        server.execute(() -> {
            Text chatText = Text.literal("[WEB] ").formatted(Formatting.AQUA)
                    .append(Text.literal(username).formatted(Formatting.WHITE))
                    .append(Text.literal(": " + message).formatted(Formatting.WHITE));

            // Broadcast to all players
            server.getPlayerManager().broadcast(chatText, false);

            // Log to server console - REMOVED to prevent double logging (broadcast likely
            // logs, or is handled elsewhere)
            // server.sendMessage(chatText);

            // Also echo back to web users
            broadcastToWeb(username, message);
        });
    }

    // Called from Game Thread (Mixin/Event)
    public static void broadcastToWeb(String sender, String message) {
        addToHistory(sender, message);

        // Broadcast to all connected websockets
        Map<String, String> payload = new java.util.HashMap<>();
        payload.put("type", "message");
        payload.put("user", sender);
        payload.put("message", message);

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
            // Get In-Game Players
            String[] players = server.getPlayerManager().getPlayerList().stream()
                    .map(p -> p.getName().getString())
                    .toArray(String[]::new);

            // Get Web Users
            String[] webUsers = sessionUsernames.values().toArray(new String[0]);

            Map<String, Object> payload = new java.util.HashMap<>();
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
        for (HistoricMessage msg : history) {
            Map<String, String> payload = new java.util.HashMap<>();
            payload.put("type", "message");
            payload.put("user", msg.user);
            payload.put("message", msg.message);
            ctx.send(GSON.toJson(payload));
        }
    }

    // Notify in-game about web joins
    public static void notifyWebJoin(String username) {
        if (server == null)
            return;
        server.execute(() -> {
            Text text = Text.literal("").append(Text.literal("[WEB] " + username + " joined the chat.")
                    .formatted(Formatting.YELLOW, Formatting.ITALIC));
            server.getPlayerManager().broadcast(text, false);
            broadcastToWeb("System", username + " joined the chat.");
            broadcastUserList(); // Update everyone's list
        });
    }

    public static void notifyWebQuit(String username) {
        if (server == null)
            return;
        server.execute(() -> {
            Text text = Text.literal("").append(Text.literal("[WEB] " + username + " left the chat.")
                    .formatted(Formatting.YELLOW, Formatting.ITALIC));
            server.getPlayerManager().broadcast(text, false);
            broadcastToWeb("System", username + " left the chat.");
            broadcastUserList(); // Update everyone's list
        });
    }

    // escape method removed as it is no longer needed with Gson

    // History Management
    private static class HistoricMessage {
        long timestamp;
        String user;
        String message;

        HistoricMessage(String u, String m) {
            this.timestamp = System.currentTimeMillis();
            this.user = u;
            this.message = m;
        }
    }

    private static final java.util.concurrent.ConcurrentLinkedDeque<HistoricMessage> history = new java.util.concurrent.ConcurrentLinkedDeque<>();

    public static void addToHistory(String user, String message) {
        history.add(new HistoricMessage(user, message));

        // Trim history
        long cutoff = System.currentTimeMillis() - (30 * 60 * 1000); // 30 mins

        // Remove old by time
        while (!history.isEmpty() && history.peek().timestamp < cutoff) {
            history.poll();
        }

        // Remove excess by count (keep max 50)
        while (history.size() > 50) {
            history.poll();
        }
    }
}
