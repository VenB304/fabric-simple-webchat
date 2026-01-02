# Fabric Simple WebChat

A lightweight, server-side Fabric mod that adds a mobile-friendly web chat interface to your Minecraft server.

## Features

*   **Real-time Chat**: Bidirectional communication between the web interface and Minecraft chat.
*   **Web Interface**:
    *   **Mobile Friendly**: Responsive design with a collapsible sidebar drawer. Back-button protection prevents accidental exits.
    *   **Theming**: Toggle between **Light Mode** and **Dark Mode**.
    *   **Customizable UI**: Configure the browser tab title and header text to match your server's identity.
    *   **Rich Chat**:
        *   **Timestamps**: Local time display for every message.
        *   **@Mentions**: 
            *   **Highlighting**: Messages mentioning you are highlighted.
            *   **Autocomplete**: Type `@` to see a dropdown of online web users.
            *   **Click-to-Mention**: Click any username to instantly tag them.
        *   **History**: Retains recent chat history (last 50 messages) for new joiners.
    *   **Notifications**:
        *   **Controls**: Mute sounds, select a custom sound/ding, or enable **Mentions Only** mode.
    *   **User Lists**: View online Minecraft players and connected Web Users.
*   **Security & Auth**:
    *   **Three Modes**: No Auth, Simple Password, or **Account Linking** (link your Minecraft account via in-game OTP).
    *   **Secure**: Rate limiting, brute-force protection, and strict session management.
    *   **SSL/TLS**: Built-in support for encrypted connections.
*   **Moderation**:
    *   **Safety**: Anti-spam rate limiting, JSON-based IP banning (`/ban-ip`), and a configurable profanity filter.

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

*   `/swc reload`: Reloads configuration and restarts the web server (without restarting Minecraft).
*   `/swc reset`: Resets configuration to defaults.
*   *Requires OP level 2.*

### Login Methods

1.  **Guest (NONE Mode)**: Enter any username.
2.  **Simple (SIMPLE Mode)**: Enter a username and the server password.
3.  **Account Linking (LINKED Mode)**:
    *   Enter your Minecraft username.
    *   Click "Get Code".
    *   Receive a 6-digit code in-game.
    *   Enter the code to verify ownership and log in.

## Configuration Guide

### `config/simple-webchat/web-chat-mod.yaml`

This file handles all server-side settings. Use `/swc reload` to apply changes instantly.

```yaml
# --- UI Customization ---
# Title of the browser tab/window
webChatTitle: "Simple WebChat"
# Text displayed in the header bar
webChatHeader: "Web Chat"

# --- Server Settings ---
webPort: 25595
trustProxy: true

# --- Authentication ---
# Options: NONE, SIMPLE, LINKED
authMode: NONE
webPassword: ""
otpRateLimitSeconds: 30

# --- Security (SSL) ---
enableSSL: false
sslKeyStorePath: ""
sslKeyStorePassword: ""

# --- Chat Limits ---
maxMessageLength: 256
maxHistoryMessages: 50
messageRetentionMinutes: 30
rateLimitMessagesPerMinute: 20

# --- Customization ---
favicon: "favicon.ico"
defaultSound: "ding.mp3"
soundPresets:
  - "ding.mp3"

# --- Moderation ---
enableProfanityFilter: false
profanityList: []
```

### Configuration Options

| Option | Default | Description |
| :--- | :--- | :--- |
| `webChatTitle` | `"Simple WebChat"` | Title shown in the browser tab. |
| `webChatHeader` | `"Web Chat"` | Text shown in the top header bar. |
| `webPort` | `25595` | Port for the web server. |
| `authMode` | `NONE` | Auth strategy: `NONE`, `SIMPLE` (password), `LINKED` (OTP). |
| `webPassword` | `""` | Password for `SIMPLE` mode. |
| `enableSSL` | `false` | Enable HTTPS (requires valid keystore). |
| `trustProxy` | `true` | Trust `X-Forwarded-For` headers (for Nginx/Cloudflare). |

### `config/simple-webchat/web-chat-bans.json`
Stores a JSON array of banned IP addresses.
[ "192.168.1.50", "10.0.0.1" ]
```

## Customization

You can provide custom assets (favicons, sounds) by placing them in the `config/simple-webchat/` directory.

### Custom Favicon
1. Place your `.ico` or `.png` file in `config/simple-webchat/`.
2. Update `web-chat-mod.yaml`:
   ```yaml
   favicon: "my-icon.png"
   ```

### Custom Sounds
1. Place `.mp3` files in `config/simple-webchat/`.
2. Update `web-chat-mod.yaml` to set the default or add to presets:
   ```yaml
   defaultSound: "custom-ding.mp3"
   soundPresets:
     - "ding.mp3"
     - "custom-ding.mp3"
     - "alert.mp3"
   ```
   Users can then select these sounds from the sidebar settings.

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
