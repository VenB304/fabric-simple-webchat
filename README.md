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
    *   **Profanity Filter**: Optional basic filter for profanity.
*   **Security**:
    *   **Authentication**: Optional simple password protection for the web interface.
    *   **SSL/TLS**: Native support for encrypted connections (WSS/HTTPS) if a keystore is provided.
    *   **Proxy Support**: Configurable support for `X-Forwarded-For` headers (default: true).
*   **Technical**:
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

This mod was built for a specific use case, but now supports optional security features for public servers.

1.  **Authentication**: By default, there is **no password**. You can enable `enableSimpleAuth` in the config to require a password.
2.  **Open Port**: This mod opens a web server on your machine (default port 25585). proper firewalling is recommended.
3.  **Basic Moderation**: Includes IP bans, rate limiting, and a basic profanity filter. It **does not** integrate with advanced moderation plugins (like AdvancedBan, LiteBans) or Discord.
4.  **TLS/SSL**: By default, runs over HTTP/WS. You can enable `enableSSL` in the config (requires a keystore) or use a reverse proxy (recommended for production).

## Configuration

### `config/web-chat-mod.json`
Generated on first launch.

```json
{
  "webPort": 25585,
  "maxMessageLength": 256,
  "rateLimitMessagesPerMinute": 20,
  "enableProfanityFilter": false,
  "webPassword": "",
  "enableSimpleAuth": false,
  "enableSSL": false,
  "sslKeyStorePath": "",
  "sslKeyStorePassword": "",
  "trustProxy": true
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
