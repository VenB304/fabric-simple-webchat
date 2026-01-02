const views = {
    loading: document.getElementById('auth-loading'),
    reconnect: document.getElementById('reconnect-msg'),
    none: document.getElementById('login-none'),
    simple: document.getElementById('login-simple'),
    linked: document.getElementById('login-linked'),
    screen: document.getElementById('login-screen'),
    main: document.getElementById('main-ui')
};

// State
let ws;
let pinger;
let myUsername = "";
let currentAuthMode = null;
let isReconnecting = false;
let reconnectDelay = 2000;

// Theme Loading
const savedTheme = localStorage.getItem('swc_theme') || 'light';
if (savedTheme === 'dark') {
    document.body.classList.add('dark-mode');
}

// Back Button Prevention
history.pushState(null, document.title, location.href);
window.addEventListener('popstate', function (event) {
    const now = new Date().getTime();
    const lastBack = window.lastBackPress || 0;

    if (now - lastBack < 2000) {
        // Allowed to go back
        history.back();
    } else {
        // Prevent, show toast/message, and push state again
        history.pushState(null, document.title, location.href);
        window.lastBackPress = now;
        showError("Press back again to exit"); // reusing showError as a toast for now
        // Or create a specific toast if showError is too alarming (red).
        // Let's stick to showError for simplicity or create a quick toast function if needed.
        // Actually showError is styled as error-msg in login box, might not be visible in main chat.
        // We need a toast.
        showToast("Press back again to exit");
    }
});

function showToast(msg) {
    let toast = document.getElementById('toast-msg');
    if (!toast) {
        toast = document.createElement('div');
        toast.id = 'toast-msg';
        toast.style.position = 'absolute';
        toast.style.bottom = '80px';
        toast.style.left = '50%';
        toast.style.transform = 'translateX(-50%)';
        toast.style.backgroundColor = 'rgba(0,0,0,0.7)';
        toast.style.color = 'white';
        toast.style.padding = '10px 20px';
        toast.style.borderRadius = '20px';
        toast.style.zIndex = '1000';
        toast.style.display = 'none';
        document.body.appendChild(toast);
    }
    toast.innerText = msg;
    toast.style.display = 'block';
    setTimeout(() => toast.style.display = 'none', 2000);
}

attemptConnect();

function toggleTheme() {
    document.body.classList.toggle('dark-mode');
    const isDark = document.body.classList.contains('dark-mode');
    localStorage.setItem('swc_theme', isDark ? 'dark' : 'light');
}

function attemptConnect() {
    if (isReconnecting && currentAuthMode !== null) {
        // If we are strictly reconnecting (known mode), show small message
        views.reconnect.style.display = 'block';
    } else {
        // Initial load
        views.loading.style.display = 'block';
    }

    const savedUser = localStorage.getItem('swc_username') || "";
    const savedPass = localStorage.getItem('swc_password') || "";
    const savedToken = localStorage.getItem('swc_token') || "";

    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
    let url = `${proto}://${window.location.host}/chat`;

    if (savedUser) {
        url += `?username=${encodeURIComponent(savedUser)}`;
        if (savedPass) url += `&password=${encodeURIComponent(savedPass)}`;
        if (savedToken) url += `&token=${encodeURIComponent(savedToken)}`;
    }
    // Do NOT append GuestProbe default. Let server handle empty username as unauthenticated/handshake.

    if (ws) {
        try { ws.close(); } catch (e) { }
    }

    ws = new WebSocket(url);

    ws.onopen = () => {
        isReconnecting = false;
        reconnectDelay = 2000;
        views.reconnect.style.display = 'none';
        views.loading.style.display = 'none'; // Only hide if we get status?
        // Actually wait for status to hide loading, but we can hide reconnect msg

        if (pinger) clearInterval(pinger);
        pinger = setInterval(() => { if (ws && ws.readyState === 1) ws.send("PING"); }, 20000);
    };

    ws.onmessage = (e) => {
        if (e.data === "PING") return;
        try {
            const data = JSON.parse(e.data);

            if (data.type === 'status') {
                handleStatus(data);
            } else if (data.type === 'message') {
                addMessage(data.user, data.message);
                // Notification Logic
                const soundSetting = localStorage.getItem('swc_sound'); // Get fresh
                const isMention = myUsername && data.message.includes("@" + myUsername);

                if (data.user !== myUsername && data.user !== "System") {
                    if (soundSetting === 'mentions-only') {
                        if (isMention) playSound();
                    } else {
                        playSound(); // Default behavior (handled inside which checks for 'none')
                    }
                }
            } else if (data.type === 'playerList') {
                updateLists(data.players, data.webUsers);
            } else if (data.type === 'otp_sent') {
                document.getElementById('linked-step-1').style.display = 'none';
                document.getElementById('linked-step-2').style.display = 'block';
            } else if (data.type === 'auth_success') {
                localStorage.setItem('swc_token', data.token);
                localStorage.setItem('swc_username', data.username);
                location.reload();
            } else if (data.type === 'error') {
                showError(data.message);
            }
        } catch (err) { }
    };

    ws.onclose = (e) => {
        // ALWAYS Retry unless user explicitly logged out (which reloads page anyway)
        // If 4003 (Auth Fail), we don't retry same creds maybe?
        // But if we are in LINKED mode handshake, we might get closed if token invalid?
        // No, we fixed AuthHandler to return HANDSHAKE_ONLY.

        isReconnecting = true;

        if (e.code === 4003 && currentAuthMode === 'SIMPLE') {
            // Password wrong? show error
            views.loading.style.display = 'none';
            views.simple.style.display = 'block';
            showError("Authentication Failed. Check password.");
            return; // Don't loop retry if creds wrong
        }

        // Show reconnecting UI if in chat
        if (views.main.style.display === 'flex') {
            addConnectionDivider("Disconnected. Reconnecting in " + (reconnectDelay / 1000) + "s...");
        } else if (currentAuthMode !== null) {
            views.reconnect.style.display = 'block';
        } else {
            views.loading.style.display = 'block';
            views.loading.innerText = "Connection lost. Retrying...";
        }

        setTimeout(attemptConnect, reconnectDelay);
        reconnectDelay = Math.min(reconnectDelay * 1.5, 15000);
    };
}

function updateSoundSetting() {
    const val = document.getElementById('sound-select').value;
    localStorage.setItem('swc_sound', val);
}

function playSound() {
    const val = document.getElementById('sound-select').value;
    // If silent
    if (!val || val === 'none') return;

    let src = val;
    if (!src.startsWith("http") && !src.startsWith("/")) {
        // Special case for default packaged sound
        if (val === "ding.mp3") {
            src = "ding.mp3"; // Relative to index.html -> /webchat-client/ding.mp3
        } else {
            src = "/custom/" + src;
        }
    }

    const audio = new Audio(src);
    audio.play().catch(e => console.log("Audio play failed (interaction needed?):", e));
}

function handleStatus(status) {
    currentAuthMode = status.authMode;
    views.loading.style.display = 'none';
    views.reconnect.style.display = 'none';

    if (status.authenticated) {
        myUsername = status.username;
        enterChat();
    } else {
        // Not authenticated
        // Update UI based on mode
        if (currentAuthMode === 'NONE') {
            setupNone();
        } else if (currentAuthMode === 'SIMPLE') {
            setupSimple();
        } else if (currentAuthMode === 'LINKED') {
            setupLinked(status.username);
        }
    }

    // Apply Favicon
    if (status.favicon) {
        let link = document.querySelector("link[rel~='icon']");
        if (!link) {
            link = document.createElement('link');
            link.rel = 'icon';
            document.head.appendChild(link);
        }
        // Check if it is a full URL or relative path
        if (status.favicon.startsWith("http") || status.favicon.startsWith("/")) {
            link.href = status.favicon;
        } else {
            // Assume it's in /custom/
            link.href = "/custom/" + status.favicon;
        }
    }

    // Setup Sounds
    const select = document.getElementById('sound-select');
    // clear existing options but keep Silent/Default? No, rebuild.
    select.innerHTML = '';

    let optSilent = document.createElement('option');
    optSilent.value = 'none';
    optSilent.innerText = 'Silent';
    select.appendChild(optSilent);

    let optMentions = document.createElement('option');
    optMentions.value = 'mentions-only';
    optMentions.innerText = 'Mentions Only';
    select.appendChild(optMentions);

    let optDefault = document.createElement('option');
    optDefault.value = status.defaultSound || 'ding.mp3'; // Fallback
    optDefault.innerText = 'Default'; // or name of file?
    select.appendChild(optDefault);

    if (status.soundPresets && Array.isArray(status.soundPresets)) {
        status.soundPresets.forEach(s => {
            // Check duplicate?
            if (s === status.defaultSound) return;

            let opt = document.createElement('option');
            opt.value = s;
            opt.innerText = s; // Filename as name
            select.appendChild(opt);
        });
    }

    // Restore selection
    const savedSound = localStorage.getItem('swc_sound');
    if (savedSound) {
        // Check if valid? or just set it
        select.value = savedSound;
    } else {
        select.value = status.defaultSound || 'ding.mp3';
    }
    if (status.webChatTitle) {
        document.title = status.webChatTitle;
        document.getElementById('login-title').innerText = status.webChatTitle;
    }
    if (status.webChatHeader) {
        document.getElementById('header-title').innerText = status.webChatHeader;
    }
}

function enterChat() {
    views.screen.style.display = 'none';
    views.main.style.display = 'flex';
    document.getElementById('current-user-display').innerText = myUsername;

    // Only toggle header if we need it (on desktop it is hidden in CSS? wait)
    // CSS says #header-bar display: none, but media-query max-width 768 display: flex
    // On desktop we might want the logout button visible? 
    // The original logic had it hidden on desktop?
    // Let's force it visible always or just use the CSS rules.
    // Actually, logout is in sidebar too.
    // But theme button is in header bar. We need header bar visible on desktop too now?
    document.getElementById('header-bar').style.display = 'flex';

    addConnectionDivider("Connected to Server");

    // Scroll to bottom
    setTimeout(() => {
        chatContainer.scrollTop = chatContainer.scrollHeight;
    }, 100);
}

// -- UI Setups --
function setupNone() {
    views.none.style.display = 'block';
    // Only auto-login if we haven't failed recently?
    // If we are here, we are NOT authenticated.
    const saved = localStorage.getItem('swc_username');
    if (saved) document.getElementById('none-username').value = saved;
}
function setupSimple() {
    views.simple.style.display = 'block';
    const user = localStorage.getItem('swc_username');
    if (user) document.getElementById('simple-username').value = user;
    // Don't autofill password for security/UX logic if it failed
}
function setupLinked(currentTempName) {
    views.linked.style.display = 'block';
    const token = localStorage.getItem('swc_token');
    const user = localStorage.getItem('swc_username');

    if (token && user) {
        document.getElementById('resume-session-container').style.display = 'block';
        document.getElementById('resume-username').innerText = user;
    }
    if (user && !document.getElementById('linked-username').value) {
        document.getElementById('linked-username').value = user;
    }
}

// -- Actions --
function loginNone() {
    const user = document.getElementById('none-username').value.trim();
    if (!user) return showError("Username required");
    localStorage.setItem('swc_username', user);
    location.reload();
}

function loginSimple() {
    const user = document.getElementById('simple-username').value.trim();
    const pass = document.getElementById('simple-password').value.trim();
    if (!user || !pass) return showError("Required fields missing");
    localStorage.setItem('swc_username', user);
    localStorage.setItem('swc_password', pass);
    location.reload();
}

function requestOtp() {
    const user = document.getElementById('linked-username').value.trim();
    if (!user) return showError("Username required");

    if (!ws || ws.readyState !== WebSocket.OPEN) {
        showError("Connection not ready. Please wait...");
        // attemptConnect() should be retrying
        return;
    }

    try {
        ws.send(JSON.stringify({ type: 'request_otp', username: user }));
    } catch (e) {
        showError("Send failed: " + e.message);
    }
}

function verifyOtp() {
    const user = document.getElementById('linked-username').value.trim();
    const code = document.getElementById('linked-code').value.trim();
    if (!user || !code) return showError("Code required");

    if (!ws || ws.readyState !== WebSocket.OPEN) return showError("Not connected.");

    ws.send(JSON.stringify({ type: 'verify_otp', username: user, code: code }));
}

function resumeSession() {
    // If we are here, the token we sent was REJECTED (authenticated=false).
    showError("Session expired. Please sign out and get a new code.");
}

function logoutLinked() {
    localStorage.removeItem('swc_token');
    document.getElementById('resume-session-container').style.display = 'none';
    resetLinkedUi();
    // Maybe reload to probe as fresh guest?
    location.reload();
}

function resetLinkedUi() {
    document.getElementById('linked-step-2').style.display = 'none';
    document.getElementById('linked-step-1').style.display = 'block';
}

function logout() {
    localStorage.removeItem('swc_username');
    localStorage.removeItem('swc_password');
    localStorage.removeItem('swc_token');
    location.reload();
}

function showError(msg) {
    const el = document.getElementById('error-msg');
    el.innerText = msg;
    el.style.display = 'block';
    setTimeout(() => el.style.display = 'none', 5000);
}

// -- DOM Helpers --
const chatContainer = document.getElementById('chat-container');
const messageInput = document.getElementById('message');
const playerListDiv = document.getElementById('player-list');
const webUserListDiv = document.getElementById('web-user-list');
const overlay = document.getElementById('overlay');
const sidebar = document.getElementById('sidebar');

function toggleSidebar() {
    sidebar.classList.toggle('open');
    overlay.classList.toggle('open');
}

function sendMessage() {
    const msg = messageInput.value.trim();
    if (!msg) return;
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(msg);
        messageInput.value = '';
    } else {
        addConnectionDivider("Cannot send: Disconnected");
    }
}
messageInput.addEventListener('keypress', (e) => { if (e.key === 'Enter') sendMessage(); });
document.getElementById('none-username').addEventListener('keypress', e => e.key === 'Enter' && loginNone());
document.getElementById('simple-password').addEventListener('keypress', e => e.key === 'Enter' && loginSimple());
document.getElementById('linked-code').addEventListener('keypress', e => e.key === 'Enter' && verifyOtp());

function addConnectionDivider(text) {
    const div = document.createElement('div');
    div.style.cssText = "display: flex; align-items: center; justify-content: center; margin: 10px 0; color: var(--text-sec); font-size: 12px; font-weight: bold; text-transform: uppercase; letter-spacing: 1px;";
    const line = "flex: 1; height: 1px; background: var(--border); margin: 0 10px;";
    div.innerHTML = `<div style="${line}"></div><span>${escapeHtml(text)}</span><div style="${line}"></div>`;
    chatContainer.appendChild(div);
    // Scroll to bottom helper
    if (chatContainer.scrollHeight - chatContainer.scrollTop < 1500) {
        chatContainer.scrollTop = chatContainer.scrollHeight;
    }
}

const COLORS = ["#e60000", "#0000e6", "#00b300", "#e6e600", "#e68a00", "#bf00bf", "#00bfbf", "#e66699", "#99e699", "#804d00"];
function getUsernameColor(username) {
    let hash = 0;
    for (let i = 0; i < username.length; i++) hash = username.charCodeAt(i) + ((hash << 5) - hash);
    return COLORS[Math.abs(hash % COLORS.length)];
}
function escapeHtml(text) { if (!text) return ''; return text.replace(/[&<>"']/g, function (m) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '\"': '&quot;', '\'': '&#039;' }[m]; }); }

// --- Global Helpers for OnClick --
window.mentionUser = function (username) {
    const input = document.getElementById('message');
    input.value += (input.value.length > 0 && !input.value.endsWith(' ') ? ' ' : '') + "@" + username + " ";
    input.focus();
}


// --- Global List State for Mention Dropdown ---
let globalWebUsers = [];

function updateLists(players, webUsers) {
    globalWebUsers = webUsers; // Store for autocomplete
    // Add onclick to list items
    playerListDiv.innerHTML = players.map(p => `<div class="user-item" style="color:${getUsernameColor(p)}" onclick="mentionUser('${escapeHtml(p)}')">${escapeHtml(p)}</div>`).join('');
    webUserListDiv.innerHTML = webUsers.map(u => `<div class="user-item" style="color:${getUsernameColor(u)}" onclick="mentionUser('${escapeHtml(u)}')">${escapeHtml(u)}</div>`).join('');
}

// Override addMessage to add onclick
function addMessage(user, text) {
    const div = document.createElement('div');
    div.className = 'message';

    if (myUsername && text.includes("@" + myUsername)) {
        div.classList.add('mention');
    }

    const color = getUsernameColor(user);

    let formattedText = escapeHtml(text);
    if (user === "System") {
        const match = text.match(/^(.+?)\s+(joined|left)/);
        if (match) {
            const name = match[1];
            const nameColor = getUsernameColor(name);
            formattedText = formattedText.replace(name, `<span style="color:${nameColor}; font-weight:bold;">${escapeHtml(name)}</span>`);
        }
    }

    const timeStr = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    const timestampHtml = `<span class="timestamp">${timeStr}</span>`;

    // Added onclick to user span
    div.innerHTML = `${timestampHtml}<span class="user" style="color: ${color}; cursor:pointer;" onclick="mentionUser('${escapeHtml(user)}')">${escapeHtml(user)}</span><span class="text">${formattedText}</span>`;

    chatContainer.appendChild(div);
    if (chatContainer.scrollHeight - chatContainer.scrollTop < 1000)
        chatContainer.scrollTop = chatContainer.scrollHeight;
}


// --- Mention Dropdown Logic ---
const dropdown = document.getElementById('mention-dropdown');
let selectedIndex = 0;
let mentionMatches = [];

messageInput.addEventListener('input', function (e) {
    const val = this.value;
    const cursorPos = this.selectionStart;

    // Find the word being typed
    const textBeforeCursor = val.substring(0, cursorPos);
    const lastAt = textBeforeCursor.lastIndexOf('@');

    if (lastAt !== -1) {
        // Check if there are spaces between @ and cursor, if so, we might have moved past it
        // BUT we want to allow "@User Na" potentially? No, username regex is strict usually.
        // Let's assume usernames have no spaces for now based on backend logic.
        const query = textBeforeCursor.substring(lastAt + 1);
        if (!query.includes(' ')) {
            // We are autocompleting
            showSuggestions(query);
            return;
        }
    }
    hideSuggestions();
});

messageInput.addEventListener('keydown', function (e) {
    if (dropdown.style.display === 'flex') {
        if (e.key === 'ArrowUp') {
            e.preventDefault();
            selectedIndex = Math.max(0, selectedIndex - 1);
            renderSuggestions();
        } else if (e.key === 'ArrowDown') {
            e.preventDefault();
            selectedIndex = Math.min(mentionMatches.length - 1, selectedIndex + 1);
            renderSuggestions();
        } else if (e.key === 'Enter' || e.key === 'Tab') {
            e.preventDefault();
            if (mentionMatches[selectedIndex]) {
                selectSuggestion(mentionMatches[selectedIndex]);
            }
        } else if (e.key === 'Escape') {
            hideSuggestions();
        }
    }
});

function showSuggestions(query) {
    // Filter webUsers
    mentionMatches = globalWebUsers.filter(u => u.toLowerCase().startsWith(query.toLowerCase()));

    if (mentionMatches.length === 0) {
        hideSuggestions();
        return;
    }

    selectedIndex = 0; // Reset
    dropdown.style.display = 'flex';
    renderSuggestions();
}

function hideSuggestions() {
    dropdown.style.display = 'none';
}

function renderSuggestions() {
    dropdown.innerHTML = '';
    // Reverse logic? HTML structure is flex-direction: column-reverse? 
    // Wait, CSS said flex-direction: column-reverse? No, I put flex-direction: column-reverse in CSS comment but wrote display:none.
    // Let's check CSS... #mention-dropdown { ... display: none; ... }
    // We should style it to appear ABOVE.
    // Using bottom: 70px which is fixed.
    // We probably want the list to be normal order, but the visual position is bottom-up?

    mentionMatches.forEach((user, idx) => {
        const div = document.createElement('div');
        div.className = 'mention-item';
        if (idx === selectedIndex) div.classList.add('selected');
        div.innerText = user;
        div.onclick = () => selectSuggestion(user);
        dropdown.appendChild(div);
    });
}

function selectSuggestion(username) {
    const val = messageInput.value;
    const cursorPos = messageInput.selectionStart;
    const textBeforeCursor = val.substring(0, cursorPos);
    const lastAt = textBeforeCursor.lastIndexOf('@');

    const before = val.substring(0, lastAt);
    const after = val.substring(cursorPos);

    messageInput.value = before + "@" + username + " " + after;
    hideSuggestions();

    // Restore focus and cursor?
    messageInput.focus();
    // cursor at end of inserted
    /*
    const newPos = (before + "@" + username + " ").length;
    messageInput.setSelectionRange(newPos, newPos);
    */
}
