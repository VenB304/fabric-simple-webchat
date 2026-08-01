package com.example.webchat.web;

import com.example.webchat.WebChatMod;
import com.example.webchat.bridge.ChatBridge;
import com.example.webchat.config.ModConfig;
import com.example.webchat.link.LinkManager;
import com.example.webchat.link.LinkResult;
import com.example.webchat.link.LinkedAccount;
import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsMessageContext;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class WebServer {
    private static final Gson GSON = new Gson();
    private static Javalin app;
    private static long startTimeMillis;

    public static void start() {
        if (app != null)
            return;

        Thread webThread = new Thread(() -> {
            try {
                ModConfig config = ModConfig.getInstance();

                app = Javalin.create(cfg -> {
                    // Serve static files from 'src/main/resources/web' (classpath)
                    cfg.staticFiles.add("/web", Location.CLASSPATH);

                    if (config.enableTls) {
                        cfg.jetty.addConnector((server, httpConfig) -> buildSslConnector(server, config));
                    }
                });

                registerHandlers(app);
                startTimeMillis = System.currentTimeMillis();

                if (config.enableTls) {
                    // The connector above is already bound to its own port; start() with no
                    // arguments so Javalin doesn't also open a plain-HTTP connector.
                    app.start();
                    WebChatMod.LOGGER.info("Web Chat Server started (WSS) on port {}", config.webPort);
                } else {
                    app.start(config.webPort);
                    WebChatMod.LOGGER.info("Web Chat Server started on port {}", config.webPort);
                }
            } catch (Exception e) {
                WebChatMod.LOGGER.error("Failed to start Web Chat Server (is the port already in use?)", e);
                app = null;
            }
        });

        webThread.setName("WebChat-Server-Thread");
        webThread.setDaemon(true);
        webThread.start();
    }

    private static ServerConnector buildSslConnector(Server server, ModConfig config) {
        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setKeyStorePath(config.tlsKeystorePath);
        sslContextFactory.setKeyStorePassword(config.tlsKeystorePassword);

        HttpConfiguration httpsConfig = new HttpConfiguration();
        httpsConfig.addCustomizer(new SecureRequestCustomizer());

        ServerConnector connector = new ServerConnector(server,
                new SslConnectionFactory(sslContextFactory, "http/1.1"),
                new HttpConnectionFactory(httpsConfig));
        connector.setPort(config.webPort);
        return connector;
    }

    private static void handleLinkRequest(WsMessageContext ctx, String code) {
        if (code.isEmpty()) {
            ctx.send("{\"type\":\"linkError\",\"message\":\"No code provided.\"}");
            return;
        }

        Optional<LinkResult> result = LinkManager.redeemCode(code.toUpperCase());
        if (result.isEmpty()) {
            ctx.send("{\"type\":\"linkError\",\"message\":\"Invalid or expired code.\"}");
            return;
        }

        String token = result.get().token();
        String username = result.get().account().username();

        ChatBridge.sessionUsernames.put(ctx, username);
        ChatBridge.sessionLinked.put(ctx, true);
        ctx.attribute("username", username);
        ctx.attribute("linked", true);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "linked");
        payload.put("username", username);
        payload.put("token", token);
        ctx.send(GSON.toJson(payload));

        ChatBridge.broadcastUserList();
        WebChatMod.LOGGER.info("Web client at {} linked as {}", ctx.attribute("ip"), username);
    }

    private static void handleUnlinkRequest(WsMessageContext ctx, String token) {
        LinkManager.revokeToken(token);
        ChatBridge.sessionLinked.put(ctx, false);
        ctx.attribute("linked", false);
        ctx.send("{\"type\":\"unlinked\"}");
        ChatBridge.broadcastUserList();
    }

    private static String resolveIp(SocketAddress remoteAddress) {
        if (remoteAddress instanceof InetSocketAddress inetAddress) {
            return inetAddress.getAddress().getHostAddress();
        }
        return "0.0.0.0";
    }

    private static void registerHandlers(Javalin app) {
        app.get("/health", ctx -> {
            Map<String, Object> payload = new HashMap<>();
            payload.put("status", "ok");
            payload.put("uptimeMillis", System.currentTimeMillis() - startTimeMillis);
            payload.put("connectedWebUsers", ChatBridge.getWebUserCount());
            ctx.contentType("application/json").result(GSON.toJson(payload));
        });

        app.ws("/chat", ws -> {
            ws.onConnect(ctx -> {
                ctx.session.setIdleTimeout(java.time.Duration.ofMinutes(5));

                ModConfig config = ModConfig.getInstance();

                if (!config.allowedOrigins.isEmpty()) {
                    String origin = ctx.header("Origin");
                    if (origin == null || !config.allowedOrigins.contains(origin)) {
                        ctx.closeSession(1008, "Origin not allowed");
                        return;
                    }
                }

                String forwarded = ctx.header("X-Forwarded-For");
                String realIp = resolveIp(ctx.session.getRemoteAddress());
                if (forwarded != null && !forwarded.isEmpty())
                    realIp = forwarded.split(",")[0].trim();

                if (ModerationManager.isBanned(realIp)) {
                    ctx.closeSession(1008, "Banned");
                    return;
                }

                if (!ModerationManager.tryAcquireConnection(realIp)) {
                    ctx.closeSession(1008, "Too many connections from your address");
                    return;
                }

                // A saved link token (from a previous /webchat link) takes priority over
                // a typed username: it proves this session belongs to that exact player.
                boolean linked = false;
                String username;
                Optional<LinkedAccount> linkedAccount = LinkManager
                        .lookupToken(ctx.queryParam("token"));
                if (linkedAccount.isPresent()) {
                    username = linkedAccount.get().username();
                    linked = true;
                } else {
                    username = ctx.queryParam("username");
                    if (username == null || username.trim().isEmpty()) {
                        username = "Guest-" + (int) (Math.random() * 1000);
                    }
                    // Sanitize
                    if (username.length() > 16)
                        username = username.substring(0, 16);
                    username = username.replaceAll("[^a-zA-Z0-9_]", ""); // Strict characters

                    // Deduplicate
                    // If the name is taken by another web user, or matches an online player's
                    // name (which would otherwise let a web user impersonate them), append digits
                    String finalName = username;
                    boolean conflict = ChatBridge.sessionUsernames.containsValue(finalName)
                            || ChatBridge.isOnlinePlayerName(finalName);
                    if (conflict) {
                        finalName = username + "_" + (int) (Math.random() * 999);
                    }
                    username = finalName;
                }

                ChatBridge.activeSessions.add(ctx);
                ChatBridge.sessionUsernames.put(ctx, username);
                ChatBridge.sessionLinked.put(ctx, linked);
                ctx.attribute("username", username);
                ctx.attribute("ip", realIp);
                ctx.attribute("linked", linked);

                WebChatMod.LOGGER.info("Web Client Connected: {} as {} (linked={})", realIp, username, linked);

                if (linked) {
                    // Authoritative confirmation, whether this came from a fresh code redemption
                    // (handled below) or a token saved from a previous visit - the client should
                    // never have to guess whether a stored token is still valid.
                    Map<String, Object> linkedPayload = new HashMap<>();
                    linkedPayload.put("type", "linked");
                    linkedPayload.put("username", username);
                    linkedPayload.put("token", ctx.queryParam("token"));
                    ctx.send(GSON.toJson(linkedPayload));
                }

                ChatBridge.notifyWebJoin(username);
                ChatBridge.sendHistoryTo(ctx);
            });

            ws.onMessage(ctx -> {
                String message = ctx.message();
                if (message.equals("PING"))
                    return;

                if (message.startsWith("LINK:") || message.startsWith("UNLINK:")) {
                    String linkIp = ctx.attribute("ip");
                    if (!ModerationManager.checkLinkRateLimit(linkIp)) {
                        ctx.send("{\"type\":\"linkError\",\"message\":\"Too many attempts, please wait a moment.\"}");
                        return;
                    }
                    if (message.startsWith("LINK:")) {
                        handleLinkRequest(ctx, message.substring("LINK:".length()).trim());
                    } else {
                        handleUnlinkRequest(ctx, message.substring("UNLINK:".length()).trim());
                    }
                    return;
                }

                String username = ctx.attribute("username");
                String ip = ctx.attribute("ip");
                Boolean linkedAttr = ctx.attribute("linked");
                boolean linked = Boolean.TRUE.equals(linkedAttr);

                if (!linked && ModConfig.getInstance().requireLinkToChat) {
                    ctx.send("{\"type\":\"message\", \"user\": \"System\", \"message\": "
                            + "\"You must link your account (run /webchat link in-game) before chatting.\"}");
                    return;
                }

                if (!ModerationManager.checkRateLimit(ip)) {
                    // Send JSON error
                    ctx.send("{\"type\":\"message\", \"user\": \"System\", \"message\": \"Rate limit exceeded.\"}");
                    return;
                }

                // Basic validation
                if (message.length() > ModConfig.getInstance().maxMessageLength) {
                    message = message.substring(0, ModConfig.getInstance().maxMessageLength);
                }

                // Sanitize color codes and control characters (newlines would otherwise break
                // the one-line-per-entry chat.log format and could forge fake-looking log lines)
                message = message.replaceAll("§.", "");
                message = message.replaceAll("\\p{Cntrl}", "");

                message = ModerationManager.filterProfanity(message);

                if (!message.trim().isEmpty()) {
                    ChatBridge.sendToGame(username, message, linked);
                }
            });

            ws.onClose(ctx -> {
                ChatBridge.removeSession(ctx);
                String username = ctx.attribute("username");
                String ip = ctx.attribute("ip");
                if (ip != null) {
                    ModerationManager.releaseConnection(ip);
                }
                if (username != null) {
                    ChatBridge.notifyWebQuit(username);
                }
            });
        });
    }

    public static void stop() {
        if (app != null) {
            app.stop();
            app = null;
        }
    }
}
