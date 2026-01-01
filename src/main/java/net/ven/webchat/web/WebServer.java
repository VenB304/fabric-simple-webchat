package net.ven.webchat.web;

import net.ven.webchat.bridge.ChatBridge;
import net.ven.webchat.auth.AuthHandler;
import net.ven.webchat.auth.AuthManager;
import net.ven.webchat.config.ModConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import net.minecraft.server.network.ServerPlayerEntity;

public class WebServer {
    private static Javalin app;
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void start() {
        if (app != null)
            return;

        Thread webThread = new Thread(() -> {
            app = Javalin.create(config -> {
                // Serve static files from 'src/main/resources/web' (classpath)
                config.staticFiles.add("/webchat-client", Location.CLASSPATH);

                // Serve custom assets from config dir
                config.staticFiles.add(sf -> {
                    sf.hostedPath = "/custom";
                    sf.directory = ModConfig.CONFIG_DIR.toFile().getAbsolutePath();
                    sf.location = Location.EXTERNAL;
                });

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

                    // Username from Query Param or Session
                    String username = ctx.attribute("username");
                    if (username == null) {
                        username = ctx.queryParam("username");
                    }

                    if (username == null || username.trim().isEmpty()) {
                        if (ModConfig.getInstance().authMode == ModConfig.AuthMode.LINKED) {
                            // Allow connection for handshake (OTP Request)
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
                    ObjectNode status = mapper.createObjectNode();
                    status.put("type", "status");
                    status.put("authenticated", isAuthenticated);
                    status.put("authMode", ModConfig.getInstance().authMode.toString());
                    status.put("username", username);
                    // Custom config
                    status.put("favicon", ModConfig.getInstance().favicon);
                    status.put("defaultSound", ModConfig.getInstance().defaultSound);
                    status.set("soundPresets", mapper.valueToTree(ModConfig.getInstance().soundPresets));

                    try {
                        ctx.send(mapper.writeValueAsString(status));
                    } catch (Exception e) {
                        net.ven.webchat.WebChatMod.LOGGER.error("Failed to send status", e);
                    }

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
                            JsonNode json = mapper.readTree(message);
                            if (json.has("type")) {
                                String type = json.get("type").asText();
                                handleJsonMessage(ctx, type, json);
                                return;
                            }
                        } catch (Exception e) {
                            // Valid JSON but maybe not a command
                        }
                    }

                    String username = ctx.attribute("username");
                    String ip = ctx.attribute("ip");

                    // Enforce Auth for Chat
                    if (!AuthHandler.isAuthorized(ctx)) {
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
                        if (containsProfanity(message)) {
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

    private static void handleJsonMessage(io.javalin.websocket.WsContext ctx, String type, JsonNode json) {
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

    private static void handleOtpRequest(io.javalin.websocket.WsContext ctx, JsonNode json,
            net.minecraft.server.MinecraftServer server) {
        String ip = ctx.attribute("ip");
        if (ip != null && !AuthManager.canRequestOtp(ip)) {
            long reset = AuthManager.getOtpResetTime(ip);
            long remaining = Math.max(0, (reset - System.currentTimeMillis()) / 1000);

            ObjectNode err = mapper.createObjectNode();
            err.put("type", "error");
            err.put("message", "Rate limit exceeded. Please wait " + remaining + "s.");
            ctx.send(err.toString());
            return;
        }

        String username = json.has("username") ? json.get("username").asText() : null;
        if (username == null || username.isEmpty())
            return;

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(username);

        if (player != null) {
            String code = AuthManager.generateOTP(player.getUuid());
            player.sendMessage(net.minecraft.text.Text.literal("§e[WebChat] Your Login Code: §b§l" + code),
                    false);

            ObjectNode msg = mapper.createObjectNode();
            msg.put("type", "otp_sent");
            ctx.send(msg.toString());
        } else {
            ObjectNode err = mapper.createObjectNode();
            err.put("type", "error");
            err.put("message", "Player not online");
            ctx.send(err.toString());
        }
    }

    private static void handleOtpVerify(io.javalin.websocket.WsContext ctx, JsonNode json,
            net.minecraft.server.MinecraftServer server) {
        String username = json.has("username") ? json.get("username").asText() : null;
        String code = json.has("code") ? json.get("code").asText() : null;

        if (username == null || code == null)
            return;

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(username);
        if (player != null) {
            if (AuthManager.verifyOTP(player.getUuid(), code)) {
                String token = AuthManager.createSession(player.getUuid(), player.getName().getString());

                ObjectNode response = mapper.createObjectNode();
                response.put("type", "auth_success");
                response.put("token", token);
                response.put("username", player.getName().getString());

                try {
                    ctx.send(mapper.writeValueAsString(response));
                } catch (Exception e) {
                    net.ven.webchat.WebChatMod.LOGGER.error("Failed to send auth success", e);
                }
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
