# Minecraft Web Chat Mod (Fabric 26.3+)

A server-side Fabric mod that embeds a lightweight web server, allowing users to chat with players in-game directly from their web browser.

## Features

*   **Real-time Chat**: Bidirectional communication between the web interface and Minecraft chat.
*   **Web Interface**:
    *   **Mobile Friendly**: Responsive design with a collapsible sidebar drawer for mobile devices.
    *   **Light/Dark Theme**: Toggle button, persisted per-browser.
    *   **@Mentions**: Highlighting when you're mentioned, `@` autocomplete dropdown (online players + web users), click any username to mention them.
    *   **Notification Sounds**: Default ding on new messages, mutable, with a "mentions only" mode.
    *   **Custom Branding**: Server owners can set the browser tab title and header text via config.
    *   **User Lists**: View currently online players (in-game) and other web users, with player head/avatar icons (source configurable — defaults to Mojang skins, can point at a self-hosted skin server).
    *   **Login System**: Simple username prompt upon connection (persisted via local storage).
    *   **Account Linking**: Optionally prove you control an in-game account via a one-time code, without a password system. Linked users get their real username, a ✓ badge, and a persistent browser token so they don't need to re-link every visit. Messages typed directly in Minecraft get their own 🎮 badge instead — typing in-game is inherently verified, but is a different claim than "this browser is linked to an account", so they're shown distinctly.
    *   **Optional Shared Password**: A separate, independent gate — require a server-wide password to connect at all, on top of (or instead of) account linking.
    *   **History**: See the recent chat history (last 50 messages) immediately upon joining, with timestamps.
    *   **Connection Status**: Visual indicators for when you connect/disconnect.
*   **Moderation**:
    *   **In-game commands**: `/webchat ban|unban|kick|list|reload`, no more hand-editing JSON.
    *   **Rate Limiting**: Built-in IP-based rate limiting to prevent spam.
    *   **Connection Cap**: Limits concurrent web sessions per IP to stop socket-flood spam.
    *   **Banning**: JSON-based ban list, manageable via commands or by hand.
    *   **Profanity Filter**: Optional, config-driven word list (disabled and empty by default).
    *   **Sanitization**: automatically strips Minecraft color codes (`§`) from web messages.
    *   **Username Deduplication**: Automatically handles duplicate usernames to prevent confusion.
    *   **Chat Log**: Every message is appended to a rotating log file on disk for moderation/audit.
*   **Technical**:
    *   **Proxy Support**: Support for `X-Forwarded-For` headers (e.g., Cloudflare Tunnels).
    *   **Optional built-in TLS**: Run HTTPS/WSS directly from a keystore, no reverse proxy required.
    *   **Performance**: Uses **Javalin** for a lightweight, high-performance web server.
    *   **Thread Safety**: Robust thread bridging between the Web thread and Minecraft Server thread.

## Installation

1.  Download the latest release `.jar`.
2.  Place it in your server's `mods` folder.
3.  Start the server.
4.  The configuration files will be generated in `config/web-chat-mod/`.

## Usage

Access the web interface via your server's IP and port (default 28660):

```
http://<your-server-ip>:28660/
```

Leaving the username field blank and clicking Connect is fine — you'll be assigned a random `Guest-####` name.

A `GET /health` endpoint is also available, returning JSON (`status`, `uptimeMillis`, `connectedWebUsers`) for uptime monitors or Crafty.

A `GET /webchat-config.json` endpoint (public, unauthenticated) exposes just the branding/UI fields (`webChatTitle`, `webChatHeader`, `defaultSound`, `passwordRequired`) so the page can apply them before login.

## Moderation Commands

All `/webchat` subcommands require the server-defined "admins" permission level.

| Command | Description |
| --- | --- |
| `/webchat ban <ip>` | Bans an IP address from the web chat. |
| `/webchat unban <ip>` | Removes an IP ban. |
| `/webchat kick <username>` | Disconnects a currently connected web user. |
| `/webchat list` | Lists currently connected web users. |
| `/webchat reload` | Reloads `web-chat-mod.json` without restarting the server (does not change the listening port). |
| `/webchat link` | **Any player** — generates a 6-character code (valid 5 minutes), sent privately, to redeem on the web chat page. |
| `/webchat unlink` | **Any player** — unlinks your own web chat account(s). |
| `/webchat unlink <username>` | Admin-only — force-unlinks another player's account (e.g. lost/compromised device). |

## Account Linking

1. In-game, run `/webchat link`. You'll get a one-time code, valid for 5 minutes.
2. On the web chat page, click **🔗 Link Account** in the sidebar and enter the code.
3. Your web session is now tied to that exact in-game username — a ✓ badge appears next to your name, you get a persistent browser token so you won't need to re-link on your next visit, and no other guest can pick your username.

Set `requireLinkToChat: true` in the config if you want unlinked guests to be able to read chat but not send messages until they link.

## ⚠️ Limitations & Security Disclaimer

This mod was built for a specific use case and might not be suitable for all public servers. Please consider the following before installing:

1.  **No Password Authentication**: There's no account/password system. Anyone can join the web chat as an unverified guest with almost any name. Account **linking** (see below) lets users prove in-game identity without a password, and `requireLinkToChat` can make it mandatory to send messages — but it's opt-in, not the default.
2.  **Open Port**: This mod opens a web server on your machine (default port 28660, deliberately uncommon to reduce collision risk with other mods/services). You must ensure this port is properly firewalled or tunneled (e.g., Cloudflare Tunnel) to prevent direct IP leaks or unauthorized access if not intended.
3.  **Basic Moderation**: While it includes IP bans, rate limiting, a connection cap, and an optional profanity filter, it **does not** integrate with advanced moderation plugins (like AdvancedBan, LiteBans) or Discord.

## Configuration

### `config/web-chat-mod/config.json`
Generated on first launch. Values are validated on load; out-of-range values are reset to their default and logged as a warning.

```json
{
  "webPort": 28660,
  "rateLimitMessagesPerMinute": 20,
  "maxMessageLength": 256,
  "maxConnectionsPerIp": 5,
  "enableProfanityFilter": false,
  "profanityWords": [],
  "historyMaxAgeMinutes": 30,
  "historyMaxCount": 50,
  "allowedOrigins": [],
  "requireLinkToChat": false,
  "linkAttemptsPerMinute": 10,
  "linkTokenExpiryDays": 0,
  "enableTls": false,
  "tlsKeystorePath": "",
  "tlsKeystorePassword": "",
  "webChatTitle": "Minecraft Web Chat",
  "webChatHeader": "Web Chat",
  "defaultSound": "ding.mp3",
  "webPassword": "",
  "showPlayerHeads": true,
  "headUrlTemplate": "https://mc-heads.net/avatar/{uuid}/20"
}
```

*   `maxConnectionsPerIp` — caps concurrent web sessions from a single IP.
*   `enableProfanityFilter` / `profanityWords` — when enabled, matching words are replaced with asterisks. Empty by default; populate `profanityWords` with whatever list you want enforced.
*   `historyMaxAgeMinutes` / `historyMaxCount` — how much chat history new web clients get replayed on connect.
*   `allowedOrigins` — if non-empty, only websocket connections whose `Origin` header exactly matches an entry in this list are accepted (e.g. `["https://chat.venb.top"]`). Leave empty while testing over a bare IP; set it once you're behind your real domain, otherwise anyone could iframe/embed your chat endpoint from another site.
*   `requireLinkToChat` — if true, unlinked (guest) web users can still connect and read chat, but can't send messages until they link via `/webchat link`. See [Account Linking](#account-linking).
*   `linkAttemptsPerMinute` — caps how many `LINK`/`UNLINK` attempts a single IP can make per minute, independent of the normal chat rate limit, so a script can't brute-force a 6-character link code by hammering guesses.
*   `linkTokenExpiryDays` — how long a persistent link token stays valid before the browser must re-link. `0` (default) means tokens never expire.
*   `showPlayerHeads` — shows a head icon beside each online player, in both the sidebar and the chat feed. The face is cropped client-side from whatever skin texture is actually on the player's `GameProfile` — the same texture Mojang sets for premium accounts, or whatever a skin-restoring mod (e.g. SkinsRestorer) patched in for an offline/cracked account. This works correctly either way with no mod-specific integration needed on our side.
*   `headUrlTemplate` — fallback only, used if a player has no resolvable skin texture (rare). Supports `{uuid}` and `{name}` placeholders, defaults to the public `mc-heads.net`.
*   `enableTls` / `tlsKeystorePath` / `tlsKeystorePassword` — lets the mod serve HTTPS/WSS directly from a Java keystore (PKCS12 or JKS) instead of requiring a reverse proxy. If you'd rather terminate TLS at a reverse proxy (Nginx, Caddy, Cloudflare Tunnel), leave this disabled.
*   `webChatTitle` / `webChatHeader` — browser tab title and in-app header text.
*   `defaultSound` — the sound file served for the "Default" notification option; must exist under the mod's `web/` resources (only `ding.mp3` ships out of the box).
*   `webPassword` — if non-empty, **every** web connection must supply this exact password to connect at all, independent of account linking. Empty (default) disables it.

A web user whose chosen name matches a currently online player, or another connected web user, is automatically suffixed with digits to prevent impersonation/confusion.

All of the mod's runtime files live under a single `config/web-chat-mod/` folder:

| File | Purpose |
| --- | --- |
| `config.json` | Settings, shown above. |
| `bans.json` | JSON array of banned IP addresses. Prefer `/webchat ban`/`/webchat unban` over hand-editing this while the server is running. |
| `links.json` | Maps persistent link tokens to the in-game account they're verified for. Managed automatically by `/webchat link`/`unlink`. |
| `chat.log` | Plain-text chat log, one line per message, rotated once it exceeds 5 MB. |

## Building from Source

This project uses Gradle and targets Minecraft 26.3, which ships unobfuscated (no Yarn mappings) via Fabric Loom.

**Prerequisites:**
*   Java 25 JDK

**Build:**
```powershell
# Windows
.\gradlew build

# Linux/Mac
./gradlew build
```
The output jar will be located in `build/libs/`. Run `./gradlew test` to run the unit test suite.

## License

MIT

## AI Acknowledgement

This project was built with significant assistance from AI coding tools — primarily **Claude** (Anthropic) — used throughout development for architecture design, code generation, debugging, and documentation. Human direction, domain knowledge, and creative decisions drove the project; AI accelerated the implementation.
