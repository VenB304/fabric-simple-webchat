package net.ven.webchat.web;

import net.ven.webchat.bridge.ChatBridge;
import net.ven.webchat.auth.AuthHandler;
import net.ven.webchat.auth.AuthManager;
import net.ven.webchat.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import net.minecraft.server.network.ServerPlayerEntity;

public class WebServer {
    private static Javalin app;
    private static final Gson gson = new Gson();

    public static void start() {
        if (app != null)
            return;

        Thread webThread = new Thread(() -> {
            app = Javalin.create(config -> {
                // Serve static files from 'src/main/resources/web' (classpath)
                config.staticFiles.add("/web", Location.CLASSPATH);

                // SSL Support
                if (ModConfig.getInstance().enableSSL) {
                    // Note: Basic SSL setup. For production, a proper keystore is needed.
                    // This assumes the user provides a valid keystore path and password.
                    if (!ModConfig.getInstance().sslKeyStorePath.isEmpty()
                            && !ModConfig.getInstance().sslKeyStorePassword.isEmpty()) {
                        config.jetty.addConnector((server, httpConfiguration) -> {
                            org.eclipse.jetty.util.ssl.SslContextFactory.Server sslContextFactory = new org.eclipse.jetty.util.ssl.SslContextFactory.Server();
                            sslContextFactory.setKeyStorePath(ModConfig.getInstance().sslKeyStorePath);
                            sslContextFactory.setKeyStorePassword(ModConfig.getInstance().sslKeyStorePassword);

                            org.eclipse.jetty.server.ServerConnector sslConnector = new org.eclipse.jetty.server.ServerConnector(
                                    server,
                                    sslContextFactory);
                            sslConnector.setPort(ModConfig.getInstance().webPort);
                            return sslConnector;
                        });
                    } else {
                        net.ven.webchat.WebChatMod.LOGGER.warn("SSL enabled but keystore path/password missing.");
                    }
                }
            });

            // WebSocket Endpoint
            app.ws("/chat", ws -> {
                ws.onConnect(ctx -> {
                    ctx.session.setIdleTimeout(java.time.Duration.ofMinutes(5));

                    // Let's re-parse for safety as attribute logic was inline.
                    String forwarded = ctx.header("X-Forwarded-For");
                    String realIp;
                    if (ctx.session.getRemoteAddress() != null) {
                        String raw = ctx.session.getRemoteAddress().toString();
                        if (raw.startsWith("/"))
                            raw = raw.substring(1);
                        int col = raw.indexOf(':');
                        if (col > 0)
                            raw = raw.substring(0, col);
                        realIp = raw;
                    } else {
                        realIp = "0.0.0.0";
                    }
                    if (ModConfig.getInstance().trustProxy && forwarded != null && !forwarded.isEmpty())
                        realIp = forwarded.split(",")[0].trim();

                    if (ModerationManager.isBanned(realIp)) {
                        ctx.closeSession(1008, "Banned");
                        return;
                    }

                    // Authentication
                    // Authentication
                    AuthHandler.AuthResult authResult = AuthHandler.handleConnection(ctx);

                    if (authResult == AuthHandler.AuthResult.FAILED) {
                        ctx.closeSession(4003, "Authentication Failed");
                        return;
                    }

                    if (authResult == AuthHandler.AuthResult.SUCCESS) {
                        ctx.attribute("authenticated", true);
                    } else {
                        // HANDSHAKE_ONLY, GUEST (Mapped to handshake now), or others
                        ctx.attribute("authenticated", false);
                        // Do NOT close, allow handshake/login UI
                    }

                    // Username from Query Param
                    // Username from Query Param or Session
                    String username = ctx.attribute("username");
                    if (username == null) {
                        username = ctx.queryParam("username");
                    }

                    if (username == null || username.trim().isEmpty()) {
                        if (ModConfig.getInstance().authMode == ModConfig.AuthMode.LINKED) {
                            // Allow connection for handshake (OTP Request)
                            // We do NOT close session here. Auth is enforced later for chat actions.
                            username = "Guest";
                        } else {
                            username = "Guest-" + (int) (Math.random() * 1000);
                        }
                    }
                    // Sanitize
                    if (username.length() > 16)
                        username = username.substring(0, 16);
                    username = username.replaceAll("[^a-zA-Z0-9_]", ""); // Strict characters

                    // Deduplicate
                    // If name is taken by another web user or ingame player, append digits
                    // Simple check: mostly for web users collision
                    String finalName = username;
                    boolean conflict = ChatBridge.sessionUsernames.containsValue(finalName);
                    if (conflict) {
                        finalName = username + "_" + (int) (Math.random() * 999);
                    }
                    username = finalName;

                    // Only register as active chat user if fully authenticated
                    boolean isAuthenticated = Boolean.TRUE.equals(ctx.attribute("authenticated"));
                    if (isAuthenticated) {
                        ChatBridge.activeSessions.add(ctx);
                        ChatBridge.sessionUsernames.put(ctx, username);
                        ctx.attribute("username", username);
                        ctx.attribute("ip", realIp);

                        net.ven.webchat.WebChatMod.LOGGER.info("Web Client Connected: " + realIp + " as " + username);

                        ChatBridge.sendHistoryTo(ctx);
                    } else {
                        // Handshake only (e.g. waiting for OTP)
                        ctx.attribute("username", username); // Temp username
                    }

                    // Send Status to Client
                    JsonObject status = new JsonObject();
                    status.addProperty("type", "status");
                    status.addProperty("authenticated", isAuthenticated);
                    status.addProperty("authMode", ModConfig.getInstance().authMode.toString());
                    status.addProperty("username", username);
                    ctx.send(gson.toJson(status));

                    // Notify Join AFTER status (so "Connected" appears before "Joined")
                    if (isAuthenticated) {
                        ChatBridge.notifyWebJoin(username);
                    }
                });

                ws.onMessage(ctx -> {
                    String message = ctx.message();
                    if (message.equals("PING"))
                        return;

                    // JSON Command Handling (OTP)
                    if (message.startsWith("{")) {
                        try {
                            JsonObject json = gson.fromJson(message, JsonObject.class);
                            if (json.has("type")) {
                                String type = json.get("type").getAsString();
                                handleJsonMessage(ctx, type, json);
                                return;
                            }
                        } catch (Exception e) {
                            // Valid JSON but maybe not a command or just chat that looks like JSON?
                            // Proceed to treat as chat if it fails specific command checks?
                            // For now, if it starts with { and parses, we assume it's a command attempt.
                            // If parsing fails, we might fall through to chat, but safer to log/ignore or
                            // treat as chat text.
                        }
                    }

                    String username = ctx.attribute("username");
                    String ip = ctx.attribute("ip");

                    // Enforce Auth for Chat
                    // Enforce Auth for Chat
                    if (!AuthHandler.isAuthorized(ctx)) {
                        // Send error?
                        return;
                    }

                    if (!ModerationManager.checkRateLimit(ip)) {
                        long reset = ModerationManager.getRateLimitReset(ip);
                        long remaining = Math.max(0, (reset - System.currentTimeMillis()) / 1000);
                        // Send JSON error
                        ctx.send(
                                "{\"type\":\"message\", \"user\": \"System\", \"message\": \"Rate limit exceeded. Try again in "
                                        + remaining + "s.\"}");
                        return;
                    }

                    // Basic validtion
                    if (message.length() > ModConfig.getInstance().maxMessageLength) {
                        message = message.substring(0, ModConfig.getInstance().maxMessageLength);
                    }

                    // Sanitize color codes
                    message = message.replaceAll("§.", "");

                    // Profanity Filter (Basic)
                    if (ModConfig.getInstance().enableProfanityFilter) {
                        // Very simple placeholder filter.
                        if (containsProfanity(message)) {
                            // Option 1: Block silent or loud
                            ctx.send(
                                    "{\"type\":\"message\", \"user\": \"System\", \"message\": \"Message blocked: contains profanity.\"}");
                            return;
                        }
                    }

                    if (!message.trim().isEmpty()) {
                        ChatBridge.sendToGame(username, message);
                    }
                });

                ws.onClose(ctx -> {
                    ChatBridge.activeSessions.remove(ctx);
                    String username = ChatBridge.sessionUsernames.remove(ctx);
                    if (username != null) {
                        ChatBridge.notifyWebQuit(username);
                    }
                });
            });

            // Start
            if (ModConfig.getInstance().enableSSL) {
                // Connector already added manually in config
                app.start();
            } else {
                app.start(ModConfig.getInstance().webPort);
            }

            net.ven.webchat.WebChatMod.LOGGER
                    .info("Web Chat Server started on port " + ModConfig.getInstance().webPort);
        });

        webThread.setName("WebChat-Server-Thread");
        webThread.setDaemon(true);
        webThread.start();
    }

    public static void stop() {
        if (app != null) {
            app.stop();
            app = null;
        }

    }

    private static void handleJsonMessage(io.javalin.websocket.WsContext ctx, String type, JsonObject json) {
        net.minecraft.server.MinecraftServer server = net.ven.webchat.bridge.ChatBridge.getServer();
        if (server == null)
            return;

        server.execute(() -> {
            switch (type) {
                case "request_otp":
                    handleOtpRequest(ctx, json, server);
                    break;
                case "verify_otp":
                    handleOtpVerify(ctx, json, server);
                    break;
                default:
                    // Unknown command
                    break;
            }
        });
    }

    private static void handleOtpRequest(io.javalin.websocket.WsContext ctx, JsonObject json,
            net.minecraft.server.MinecraftServer server) {
        String ip = ctx.attribute("ip");
        if (ip != null && !AuthManager.canRequestOtp(ip)) {
            long reset = AuthManager.getOtpResetTime(ip);
            long remaining = Math.max(0, (reset - System.currentTimeMillis()) / 1000);
            ctx.send("{\"type\": \"error\", \"message\": \"Rate limit exceeded. Please wait " + remaining + "s.\"}");
            return;
        }

        String username = json.has("username") ? json.get("username").getAsString() : null;
        if (username == null || username.isEmpty())
            return;

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(username);

        if (player != null) {
            String code = AuthManager.generateOTP(player.getUuid());
            player.sendMessage(net.minecraft.text.Text.literal("§e[WebChat] Your Login Code: §b§l" + code),
                    false);
            ctx.send("{\"type\": \"otp_sent\"}");
        } else {
            ctx.send("{\"type\": \"error\", \"message\": \"Player not online\"}");
        }
    }

    private static void handleOtpVerify(io.javalin.websocket.WsContext ctx, JsonObject json,
            net.minecraft.server.MinecraftServer server) {
        String username = json.has("username") ? json.get("username").getAsString() : null;
        String code = json.has("code") ? json.get("code").getAsString() : null;

        if (username == null || code == null)
            return;

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(username);
        if (player != null) {
            if (AuthManager.verifyOTP(player.getUuid(), code)) {
                String token = AuthManager.createSession(player.getUuid(), player.getName().getString());
                ctx.send("{\"type\": \"auth_success\", \"token\": \"" + token + "\", \"username\": \""
                        + player.getName().getString() + "\"}");
            } else {
                ctx.send("{\"type\": \"error\", \"message\": \"Invalid Code\"}");
            }
        } else {
            ctx.send("{\"type\": \"error\", \"message\": \"Player not online to verify\"}");
        }
    }

    private static boolean containsProfanity(String message) {
        String lower = message.toLowerCase();
        for (String word : ModConfig.getInstance().profanityList) {
            if (lower.contains(word.toLowerCase()))
                return true;
        }
        return false;
    }
}
