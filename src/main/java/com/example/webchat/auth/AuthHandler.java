package com.example.webchat.auth;

import com.example.webchat.config.ModConfig;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsConnectContext;

public class AuthHandler {

    public enum AuthResult {
        SUCCESS,
        FAILED,
        GUEST,
        HANDSHAKE_ONLY // Handling authentication (OTP) but not yet fully authenticated for chat
    }

    public static AuthResult handleConnection(WsConnectContext ctx) {
        ModConfig.AuthMode mode = ModConfig.getInstance().authMode;

        switch (mode) {
            case NONE:
                return AuthResult.GUEST;

            case SIMPLE:
                String pass = ctx.queryParam("password");
                String configPass = ModConfig.getInstance().webPassword;
                if (pass != null && pass.equals(configPass)) {
                    return AuthResult.SUCCESS;
                }
                return AuthResult.FAILED;

            case LINKED:
                String token = ctx.queryParam("token");
                AuthManager.Session session = AuthManager.verifySession(token);
                if (session != null) {
                    ctx.attribute("uuid", session.uuid);
                    ctx.attribute("username", session.username);
                    return AuthResult.SUCCESS;
                }
                // Allow connection for OTP handshake, but restricts chat
                return AuthResult.HANDSHAKE_ONLY;

            default:
                return AuthResult.FAILED;
        }
    }

    public static boolean isAuthorized(WsContext ctx) {
        ModConfig.AuthMode mode = ModConfig.getInstance().authMode;

        if (mode == ModConfig.AuthMode.NONE)
            return true;

        if (mode == ModConfig.AuthMode.SIMPLE) {
            // Simple auth is checked at connection time
            // If we are here, we are assuming connection was successful or logic handled
            // it.
            // However, for statelessness or robustness, we might want to track it in
            // session attribute.
            // For now, mirroring previous logic: if they connected, they are good.
            return true;
        }

        if (mode == ModConfig.AuthMode.LINKED) {
            return Boolean.TRUE.equals(ctx.attribute("authenticated"));
        }

        return false;
    }
}
