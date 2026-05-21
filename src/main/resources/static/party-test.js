const BASE = "/api/watchalong/party";
let currentPartyId = null;
let currentUserId = null;
let isHost = false;
let sseAbortController = null;
let heartbeatTimer = null;

// ---- Auth ----
function getToken() {
  return document.getElementById("token").value.trim();
}

function parseToken() {
  const t = getToken();
  if (!t) return;
  try {
    const payload = JSON.parse(atob(t.split(".")[1]));
    currentUserId = payload.sub;
    document.getElementById("tokenStatus").textContent =
      `✓ user: ${payload.sub?.substring(0, 8)}...`;
    document.getElementById("tokenStatus").style.color = "#4ade80";
  } catch (e) {
    document.getElementById("tokenStatus").textContent = "Invalid JWT";
    document.getElementById("tokenStatus").style.color = "#f87171";
  }
}

function headers() {
  return {
    "Content-Type": "application/json",
    Authorization: `Bearer ${getToken()}`,
  };
}

// ---- API Calls ----
async function createParty() {
  const mediaType = document.getElementById("createMediaType").value;
  const body = {
    mediaType,
    tmdbId: parseInt(document.getElementById("createTmdbId").value),
    providerId: document.getElementById("createProviderId").value.trim(),
    seasonNo:
      mediaType !== "Movie"
        ? parseInt(document.getElementById("createSeason").value)
        : 0,
    episodeNo:
      mediaType !== "Movie"
        ? parseInt(document.getElementById("createEpisode").value)
        : 0,
  };
  try {
    const res = await fetch(`${BASE}/create`, {
      method: "POST",
      headers: headers(),
      body: JSON.stringify(body),
    });
    const data = await res.json();
    if (!res.ok) return alert("Error: " + JSON.stringify(data));
    onPartyJoined(data, true);
  } catch (e) {
    alert("Network error: " + e.message);
  }
}

async function joinParty() {
  const code = document.getElementById("joinCode").value.trim().toUpperCase();
  if (code.length !== 8)
    return alert("Party code must be exactly 8 characters");
  try {
    const res = await fetch(`${BASE}/join`, {
      method: "POST",
      headers: headers(),
      body: JSON.stringify({ partyId: code }),
    });
    const data = await res.json();
    if (!res.ok) return alert("Error: " + JSON.stringify(data));
    onPartyJoined(data, false);
  } catch (e) {
    alert("Network error: " + e.message);
  }
}

async function leaveParty() {
  if (!currentPartyId) return;
  try {
    await fetch(`${BASE}/leave?partyId=${currentPartyId}`, {
      method: "POST",
      headers: headers(),
    });
  } finally {
    onPartyLeft();
  }
}

async function pushProgress() {
  if (!currentPartyId) return;
  const body = {
    partyId: currentPartyId,
    progress: parseFloat(document.getElementById("hostProgress").value) || 0,
    providerId: document.getElementById("createProviderId").value.trim(),
  };
  const res = await fetch(`${BASE}/progress`, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const d = await res.json();
    alert("Error: " + JSON.stringify(d));
  }
}

async function sendHeartbeat() {
  if (!currentPartyId) return;
  await fetch(`${BASE}/heartbeat?partyId=${currentPartyId}`, {
    method: "POST",
    headers: headers(),
  });
}

async function sendChat() {
  const input = document.getElementById("chatInput");
  const content = input.value.trim();
  if (!content || !currentPartyId) return;
  const res = await fetch(`${BASE}/chat`, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify({ partyId: currentPartyId, content }),
  });
  if (res.ok) input.value = "";
  else {
    const d = await res.json();
    alert("Error: " + JSON.stringify(d));
  }
}

async function doSync() {
  if (!currentPartyId) return;
  const res = await fetch(`${BASE}/${currentPartyId}/sync`, {
    headers: headers(),
  });
  const data = await res.json();
  alert(data.streamUrl ? `Sync URL:\n${data.streamUrl}` : JSON.stringify(data));
}

// ---- SSE ----
function connectSSE(partyId) {
  // Abort any existing SSE stream before starting a new one
  if (sseAbortController) {
    sseAbortController.abort();
    sseAbortController = null;
  }
  sseAbortController = new AbortController();
  fetchSSE(partyId, getToken(), sseAbortController.signal);
}

async function fetchSSE(partyId, token, signal) {
  try {
    const res = await fetch(`${BASE}/events?partyId=${partyId}`, {
      signal,
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "text/event-stream",
        "Cache-Control": "no-cache",
      },
    });
    if (!res.ok) {
      addEvent("ERROR", { status: res.status, partyId });
      return;
    }

    document.getElementById("sseStatus").classList.add("on");
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    let eventName = "message";
    let dataLine = "";

    // Also abort reading when the signal fires
    signal.addEventListener("abort", () => {
      reader.cancel();
    });

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop();

      for (const line of lines) {
        const cleanLine = line.trim();
        if (cleanLine.startsWith("event:"))
          eventName = cleanLine.slice(6).trim();
        else if (cleanLine.startsWith("data:"))
          dataLine = cleanLine.slice(5).trim();
        else if (cleanLine === "" && dataLine) {
          handleSSEEvent(eventName, dataLine);
          eventName = "message";
          dataLine = "";
        }
      }
    }
  } catch (e) {
    // AbortError is expected when we manually cancel — don't log it as an error
    if (e.name !== "AbortError") {
      document.getElementById("sseStatus").classList.remove("on");
      addEvent("SSE_ERROR", { error: e.message });
    }
  }
}

function handleSSEEvent(eventName, dataStr) {
  try {
    const data = JSON.parse(dataStr);
    addEvent(eventName, data.payload || data);

    switch (eventName) {
      case "NEW_CHAT":
        appendChatMessage(data.payload, data.triggeredById === currentUserId);
        break;
      case "PARTY_STATE_UPDATED":
        document.getElementById("infoProgress").textContent =
          (data.payload?.progress ?? "—") + "s";
        break;
      case "HOST_MIGRATED":
        isHost = data.payload?.newHostId === currentUserId;
        document.getElementById("hostControls").style.display = isHost
          ? "flex"
          : "none";
        break;
      case "PARTY_ENDED":
        onPartyLeft();
        break;
      case "USER_JOINED":
      case "USER_LEFT":
      case "USER_EVICTED":
        refreshPartyState();
        break;
    }
  } catch (e) {
    console.warn("SSE parse error", e);
  }
}

// ---- UI State ----
function copyShareLink() {
  if (!currentPartyId) return;
  const url = new URL(window.location.href);
  url.searchParams.set("party", currentPartyId);
  navigator.clipboard
    .writeText(url.toString())
    .then(() => {
      addEvent("SYSTEM", { message: "Share link copied to clipboard!" });
    })
    .catch((err) => {
      alert("Failed to copy link: " + err);
    });
}

function onPartyJoined(data, host) {
  currentPartyId = data.partyId;
  isHost = host;

  document.getElementById("activeParty").style.display = "flex";
  document.getElementById("hostControls").style.display = host
    ? "flex"
    : "none";
  document.getElementById("joinCode").value = data.partyId;

  updatePartyUI(data);

  // Render history; clear placeholder
  const chatArea = document.getElementById("chatArea");
  chatArea.innerHTML = "";
  if (data.recentMessages && data.recentMessages.length > 0) {
    data.recentMessages.forEach((m) =>
      appendChatMessage(m, m.senderId === currentUserId),
    );
  } else {
    chatArea.innerHTML = '<div class="placeholder">No messages yet</div>';
  }

  connectSSE(currentPartyId);

  // Auto heartbeat every 2.5 min to stay under the 5-min zombie threshold
  if (heartbeatTimer) clearInterval(heartbeatTimer);
  heartbeatTimer = setInterval(sendHeartbeat, 2.5 * 60 * 1000);
}

function onPartyLeft() {
  currentPartyId = null;
  isHost = false;

  // Kill the SSE stream
  if (sseAbortController) {
    sseAbortController.abort();
    sseAbortController = null;
  }
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }

  document.getElementById("sseStatus").classList.remove("on");
  document.getElementById("activeParty").style.display = "none";
  document.getElementById("chatArea").innerHTML =
    '<div class="placeholder">Join a party to start chatting</div>';
  addEvent("SYSTEM", { message: "You have left the party" });
}

function updatePartyUI(data) {
  document.getElementById("partyCodeDisplay").textContent = data.partyId || "—";
  document.getElementById("infoHost").textContent = data.hostId
    ? data.hostId.substring(0, 8) + "..."
    : "—";
  document.getElementById("infoProgress").textContent =
    (data.progress ?? 0) + "s";
}

async function refreshPartyState() {
  if (!currentPartyId) return;
  try {
    const res = await fetch(`${BASE}/${currentPartyId}`, {
      headers: headers(),
    });
    if (res.ok) {
      const d = await res.json();
      updatePartyUI(d);
    }
  } catch (e) {
    // silently ignore
  }
}

// ---- Chat UI ----
function appendChatMessage(msg, mine = false) {
  const area = document.getElementById("chatArea");
  // Remove the placeholder if present
  const placeholder = area.querySelector(".placeholder");
  if (placeholder) placeholder.remove();

  const div = document.createElement("div");
  div.className = "chat-msg" + (mine ? " mine" : "");
  const time = msg.sentAt ? new Date(msg.sentAt).toLocaleTimeString() : "";
  div.innerHTML = `
    <div class="chat-meta">
      <span class="chat-sender">${esc(msg.senderName || "Unknown")}</span>
      <span>${time}</span>
    </div>
    <div class="chat-body">${esc(msg.content)}</div>
  `;
  area.appendChild(div);
  area.scrollTop = area.scrollHeight;
}

// ---- Events UI ----
function addEvent(name, payload) {
  const panel = document.getElementById("eventsPanel");
  // Remove the placeholder if present
  const placeholder = panel.querySelector(".placeholder");
  if (placeholder) placeholder.remove();

  const div = document.createElement("div");
  div.className = "event-item";
  const payloadStr =
    typeof payload === "object"
      ? JSON.stringify(payload, null, 2)
      : String(payload);

  let color = "rgba(255,255,255,0.08)";
  if (name.includes("JOINED") || name.includes("CONNECTED"))
    color = "rgba(74, 222, 128, 0.2)";
  if (
    name.includes("LEFT") ||
    name.includes("EVICTED") ||
    name.includes("ERROR") ||
    name.includes("ENDED")
  )
    color = "rgba(248, 113, 113, 0.2)";
  if (name.includes("UPDATED")) color = "rgba(94, 234, 212, 0.2)";
  if (name.includes("CHAT")) color = "rgba(124, 106, 247, 0.2)";

  div.innerHTML = `
    <div class="event-badge" style="background:${color}">${name}</div>
    <div class="event-payload">${esc(payloadStr)}</div>
  `;
  panel.prepend(div);
}

function esc(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

document
  .getElementById("createMediaType")
  .addEventListener("change", function () {
    document.getElementById("tvFields").style.display =
      this.value !== "Movie" ? "grid" : "none";
  });

window.addEventListener("DOMContentLoaded", () => {
  const params = new URLSearchParams(window.location.search);
  const partyId = params.get("party");
  if (partyId) {
    document.getElementById("joinCode").value = partyId;
  }
});
