# Fabric Simple WebChat

A lightweight, server-side Fabric mod that adds a mobile-friendly web chat interface to your Minecraft server.

## Features

*   **Real-time Chat**: Bidirectional communication between the web interface and Minecraft chat.
*   **Web Interface**:
    *   **Mobile Friendly**: Responsive design with a collapsible sidebar drawer for mobile devices.
    *   **Dark Mode**: Sleek, modern dark UI.
    *   **User Lists**: View currently online players (in-game) and other web users.
    *   **Login System**:
        *   **Simple Auth**: Shared password protection.
        *   **Account Linking (OTP)**: Securely link your Minecraft account to the web chat using an in-game One-Time Password.
    *   **History**: See the recent chat history (last 50 messages) immediately upon joining.
    *   **Connection Status**: Visual indicators for when you connect/disconnect.
*   **Moderation**:
    *   **Rate Limiting**: Built-in IP-based rate limiting to prevent spam.
    *   **Banning**: `/ban-ip` style JSON-based ban list support.
    *   **Sanitization**: automatically strips Minecraft color codes (`§`) from web messages.
    *   **Username Deduplication**: Automatically handles duplicate usernames to prevent confusion.
    *   **Profanity Filter**: Optional basic filter for profanity.
*   **Security**:
    *   **Flexible Auth Modes**: Choose between No Auth, Simple Password, or Account Linking.
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
http://<your-server-ip>:25585/
```

### Login Methods

1.  **Guest/Simple**: Enter a username (and password if enabled) to join as a web user.
2.  **Account Linking**:
    *   Click "Login to Minecraft Account".
    *   Enter your Minecraft username.
    *   Click "Get Code".
    *   You will receive a code in-game (must be online).
    *   Enter the code in the web interface to verify and link your account.

## Configuration Guide

### `config/web-chat-mod.json`

Generated on first launch.

```json
{
  "webPort": 25585,
  "maxMessageLength": 256,
  "rateLimitMessagesPerMinute": 20,
  "enableProfanityFilter": false,
  "webPassword": "",
  "authMode": "NONE",
  "enableSSL": false,
  "sslKeyStorePath": "",
  "sslKeyStorePassword": "",
  "trustProxy": true
}
```

### Configuration Options

| Option | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `webPort` | `int` | `25585` | The port the web server listens on. |
| `maxMessageLength` | `int` | `256` | Maximum characters allowed in a single message. |
| `rateLimitMessagesPerMinute` | `int` | `20` | Max messages a user can send per minute before being rate-limited. |
| `authMode` | `enum` | `NONE` | Authentication mode. Options: `NONE`, `SIMPLE`, `LINKED`. |
| `webPassword` | `string` | `""` | Password required if `authMode` is `SIMPLE`. |
| `enableProfanityFilter` | `boolean` | `false` | Enables a basic swear word filter. |
| `enableSSL` | `boolean` | `false` | Enables HTTPS/WSS. Requires keystore. |
| `sslKeyStorePath` | `string` | `""` | Path to the JKS/PKCS12 keystore file for SSL. |
| `sslKeyStorePassword` | `string` | `""` | Password for the keystore. |
| `trustProxy` | `boolean` | `true` | Trust `X-Forwarded-For` headers (useful behind Nginx/Cloudflare). |

### Authentication Modes (`authMode`)

*   **`NONE`**: Open access. Anyone can join with any username (duplicates prevented).
*   **`SIMPLE`**: All users must enter the global `webPassword` to join.
*   **`LINKED`**: Users must verify ownership of their Minecraft account via an in-game OTP code.

### `config/web-chat-bans.json`
Stores a JSON array of banned IP addresses.
```json
[ "192.168.1.50", "10.0.0.1" ]
```

## ⚠️ Security & Limitations

1.  **Open Port**: This mod opens a web server on your machine (default port 25585). **Ensure you configure your firewall correctly.**
2.  **Moderation**: While basic tools (bans, filters) are included, this mod **does not** integrate with advanced plugins like LiteBans or Discord.
3.  **HTTPS**: By default, traffic is unencrypted (HTTP). For production use, enable SSL with a keystore or use a reverse proxy (Nginx/Cloudflare).

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
