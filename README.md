# Fabric Simple WebChat

A lightweight, server-side Fabric mod that adds a mobile-friendly web chat interface to your Minecraft server.

## Features

*   **Real-time Chat**: Bidirectional communication between the web interface and Minecraft chat.
*   **Web Interface**:
    *   **Mobile Friendly**: Responsive design with a collapsible sidebar drawer for mobile devices.
    *   **Theming**: **Light Mode** (default) and **Dark Mode**, with a persistent toggle.
    *   **User Lists**: View currently online players (in-game) and other web users.
    *   **Chat Features**:
        *   **Timestamps**: Local time display for every message.
        *   **Mentions**: Highlights messages that mention your username.
        *   **History**: See the recent chat history (last 50 messages) immediately upon joining.
    *   **Login System**:
        *   **Simple Auth**: Shared password protection.
        *   **Account Linking (OTP)**: Securely link your Minecraft account to the web chat using an in-game One-Time Password.
*   **Moderation**:
    *   **Rate Limiting**: IP-based rate limiting for messages and OTP requests.
    *   **Banning**: `/ban-ip` style JSON-based ban list support.
    *   **Sanitization**: Automatically strips Minecraft color codes (`§`) from web messages.
    *   **Profanity Filter**: Configurable list of banned words.
*   **Security**:
    *   **Flexible Auth Modes**: Choose between No Auth, Simple Password, or Account Linking.
    *   **SSL/TLS**: Native support for encrypted connections (WSS/HTTPS) if a keystore is provided.
    *   **Proxy Support**: Configurable support for `X-Forwarded-For` headers (default: true).
*   **Technical**:
    *   **Performance**: Uses **Javalin** for a lightweight, high-performance web server.
    *   **Refactored Client**: Modular web client structure for better caching and maintainability.
    *   **Thread Safety**: Robust thread bridging between the Web thread and Minecraft Server thread.
    *   **Resource Management**: Automatic cleanup of expired sessions and stale data.

## Installation

1.  Download the latest release `.jar`.
2.  Place it in your server's `mods` folder.
3.  Start the server.
4.  The configuration files will be generated in `config/simple-webchat/`.

## Usage

Access the web interface via your server's IP and port (default 25595):

```
http://<your-server-ip>:25595/
```

### Commands

*   `/swc reload` (or `/simplewebchat reload`): Reloads the configuration and restarts the web server without stopping the Minecraft server.
*   `/swc reset` (or `/simplewebchat reset`): Resets the configuration to defaults and restarts the web server.
*   *Requires OP level 2.*

### Login Methods

1.  **Guest (NONE Mode)**: Enter a username to join.
2.  **Simple (SIMPLE Mode)**: Enter a username and the server-configured password.
3.  **Account Linking (LINKED Mode)**:
    *   Enter your Minecraft username.
    *   Click "Get Code".
    *   You will receive a 6-digit code in-game (you must be online).
    *   Enter the code in the web interface to verify and link your account.

## Configuration Guide

### `config/simple-webchat/web-chat-mod.yaml`

Generated on first launch.

```yaml
# Fabric Simple WebChat Configuration
# You can rearrange these options and add comments.

# --- Server Settings ---
# The port the web server will listen on.
webPort: 25595
# Set to true if running behind a reverse proxy (e.g. Nginx, Cloudflare) to correctly identify client IPs.
trustProxy: true

# --- Authentication ---
# Options: NONE, SIMPLE, LINKED
# NONE: No authentication required.
# SIMPLE: Single password for all users.
# LINKED: Users must link their Minecraft account via OTP.
authMode: NONE

# Required if AuthMode is SIMPLE.
webPassword: ""

# Seconds between OTP requests (LINKED mode).
otpRateLimitSeconds: 30

# --- Security (SSL) ---
# Enable built-in SSL support (not recommended if using a reverse proxy).
enableSSL: false
# Path to PKCS12 or JKS keystore.
sslKeyStorePath: ""
# Password for the keystore.
sslKeyStorePassword: ""

# --- Chat Limits ---
maxMessageLength: 256
# Number of messages to keep in memory for history implementation.
maxHistoryMessages: 50
# Max age of messages in memory (minutes).
messageRetentionMinutes: 30
rateLimitMessagesPerMinute: 20

# --- Customization ---
# URL to an icon or path (relative to config dir or absolute)
favicon: "favicon.ico"
# Default sound file name
defaultSound: "ding.mp3"
# List of available sounds in the sidebar
soundPresets:
  - "ding.mp3"

# --- Moderation ---
enableProfanityFilter: false
# List of words to filter
profanityList:
  - "exampleBadWord1"
  - "exampleBadword2"
```

### Configuration Options

| Option | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `webPort` | `int` | `25595` | The port the web server listens on. |
| `trustProxy` | `boolean` | `true` | Trust `X-Forwarded-For` headers (useful behind Nginx/Cloudflare). |
| `authMode` | `enum` | `NONE` | Authentication mode (`NONE`, `SIMPLE`, `LINKED`). |
| `webPassword` | `string` | `""` | Password required if `authMode` is `SIMPLE`. |
| `otpRateLimitSeconds` | `int` | `30` |  Cooldown for requesting OTP codes. |
| `enableSSL` | `boolean` | `false` | Enables HTTPS/WSS. Requires keystore. |
| `sslKeyStorePath` | `string` | `""` | Path to the JKS/PKCS12 keystore file for SSL. |
| `maxMessageLength` | `int` | `256` | Maximum characters allowed in a single message. |
| `enableProfanityFilter` | `boolean` | `false` | Enables the profanity filter. |
| `profanityList` | `list` | `[]` | List of words to filter if enabled. |

### `config/simple-webchat/web-chat-bans.json`
Stores a JSON array of banned IP addresses.
```json
[ "192.168.1.50", "10.0.0.1" ]
```

## ⚠️ Security & Limitations

1.  **Open Port**: This mod opens a web server on your machine (default port 25595). **Ensure you configure your firewall correctly.**
2.  **HTTPS**: By default, traffic is unencrypted (HTTP). For production use, enable SSL with a keystore or use a reverse proxy (Nginx/Cloudflare).

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
