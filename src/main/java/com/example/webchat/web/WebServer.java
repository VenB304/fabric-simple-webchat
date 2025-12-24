package com.example.webchat.web;

import com.example.webchat.bridge.ChatBridge;
import com.example.webchat.config.ModConfig;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class WebServer {
    private static Javalin app;

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
                        com.example.webchat.WebChatMod.LOGGER.warn("SSL enabled but keystore path/password missing.");
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
                    if (ModConfig.getInstance().enableSimpleAuth) {
                        String pass = ctx.queryParam("password");
                        if (pass == null || !pass.equals(ModConfig.getInstance().webPassword)) {
                            ctx.closeSession(4003, "Forbidden: Invalid Password");
                            return;
                        }
                    }

                    // Username from Query Param
                    // Username from Query Param
                    String username = ctx.queryParam("username");
                    if (username == null || username.trim().isEmpty()) {
                        username = "Guest-" + (int) (Math.random() * 1000);
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

                    ChatBridge.activeSessions.add(ctx);
                    ChatBridge.sessionUsernames.put(ctx, username);
                    ctx.attribute("username", username);
                    ctx.attribute("ip", realIp);

                    com.example.webchat.WebChatMod.LOGGER.info("Web Client Connected: " + realIp + " as " + username);

                    ChatBridge.notifyWebJoin(username);
                    ChatBridge.sendHistoryTo(ctx);
                });

                ws.onMessage(ctx -> {
                    String message = ctx.message();
                    if (message.equals("PING"))
                        return;

                    String username = ctx.attribute("username");
                    String ip = ctx.attribute("ip");

                    if (!ModerationManager.checkRateLimit(ip)) {
                        // Send JSON error
                        ctx.send("{\"type\":\"message\", \"user\": \"System\", \"message\": \"Rate limit exceeded.\"}");
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
            app.start(ModConfig.getInstance().webPort);
            com.example.webchat.WebChatMod.LOGGER
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

    private static boolean containsProfanity(String message) {
        String lower = message.toLowerCase();
        // A few basic words. Users should really use a proper moderation bot/plugin if
        // they need advanced stuff.
        String[] badWords = { "badword", "naughty" };
        for (String word : badWords) {
            if (lower.contains(word))
                return true;
        }
        return false;
    }
}
