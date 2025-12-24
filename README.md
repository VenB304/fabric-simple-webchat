# Fabric Simple WebChat

A lightweight, server-side Fabric mod that adds a mobile-friendly web chat interface to your Minecraft server.

## Features

*   **Real-time Chat**: Bidirectional communication between the web interface and Minecraft chat.
*   **Web Interface**:
    *   **Mobile Friendly**: Responsive design with a collapsible sidebar drawer for mobile devices.
    *   **Dark Mode**: Sleek, modern dark UI.
    *   **User Lists**: View currently online players (in-game) and other web users.
    *   **Login System**: Simple username prompt upon connection (persisted via local storage).
    *   **History**: See the recent chat history (last 50 messages) immediately upon joining.
    *   **Connection Status**: Visual indicators for when you connect/disconnect.
*   **Moderation**:
    *   **Rate Limiting**: Built-in IP-based rate limiting to prevent spam.
    *   **Banning**: `/ban-ip` style JSON-based ban list support.
    *   **Sanitization**: automatically strips Minecraft color codes (`§`) from web messages.
    *   **Username Deduplication**: Automatically handles duplicate usernames to prevent confusion.
*   **Technical**:
    *   **Proxy Support**: Support for `X-Forwarded-For` headers (e.g., Cloudflare Tunnels).
    *   **Performance**: Uses **Javalin** for a lightweight, high-performance web server.
    *   **Thread Safety**: Robust thread bridging between the Web thread and Minecraft Server thread.

## Installation

1.  Download the latest release `.jar`.
2.  Place it in your server's `mods` folder.
3.  Start the server.
4.  The configuration files will be generated in `config/`.

## Usage

Access the web interface via your server's IP and port (default 25585):

```
http://<your-server-ip>:25585/web/index.html
```

> **Note**: You must append `/web/index.html` to the URL.

## ⚠️ Limitations & Security Disclaimer

This mod was built for a specific use case and might not be suitable for all public servers. Please consider the following before installing:

1.  **No Authentication**: There is **no password or account verification**. Anyone can join the web chat and choose almost any username. This makes it unsuitable for communities where identity verification is critical.
2.  **Open Port**: This mod opens a web server on your machine (default port 25585). You must ensure this port is properly firewalled or tunneled (e.g., Cloudflare Tunnel) to prevent direct IP leaks or unauthorized access if not intended.
3.  **Basic Moderation**: While it includes simple IP bans and rate limiting, it **does not** integrate with advanced moderation plugins (like AdvancedBan, LiteBans) or Discord.
4.  **No TLS/SSL**: The internal server runs over HTTP/WS. For secure production use (HTTPS/WSS), you **must** run this behind a reverse proxy (Nginx, Caddy, Cloudflare) that handles SSL termination.

## Configuration

### `config/web-chat-mod.json`
Generated on first launch.

```json
{
  "webPort": 25585,
  "maxMessageLength": 256,
  "rateLimitMessagesPerMinute": 20
}
```

### `config/web-chat-bans.json`
Stores a JSON array of banned IP addresses.
```json
[ "192.168.1.50", "10.0.0.1" ]
```

## Building from Source

This project uses Gradle.

**Prerequisites:**
*   Java 21 JDK

**Build:**
```powershell
# Windows
.\gradlew build

# Linux/Mac
./gradlew build
```
The output jar will be located in `build/libs/`.

## License

MIT
