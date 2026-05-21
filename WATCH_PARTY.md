# Watch Party — Full Implementation Guide

## Contents

- [Architecture Overview](#architecture-overview)
- [Redis Data Model](#redis-data-model)
- [REST API Reference](#rest-api-reference)
- [Request & Response Shapes](#request--response-shapes)
- [SSE Event Stream](#sse-event-stream)
- [SSE Event Catalog](#sse-event-catalog)
- [Backend Internals](#backend-internals)
- [Frontend Integration Guide](#frontend-integration-guide)
- [Full Frontend Reference Implementation](#full-frontend-reference-implementation)
- [Concurrency & Safety Notes](#concurrency--safety-notes)

---

## Architecture Overview

The watch party system combines **stateless HTTP REST** for commands with **Server-Sent Events (SSE)** for real-time delivery. Redis is the single source of truth for party state; Redis Pub/Sub fans events out to every connected client across all server instances.

```
Client (REST command)
    └─► PartyController
            └─► PartyService (mutates Redis state + acquires ReentrantLock)
                    └─► PartyEventPublisher → Redis Pub/Sub (party:{id}:events)
                                                    └─► PartyEventSubscriber
                                                              └─► SseEmitterRegistry
                                                                        └─► Client (SSE stream)
```

**Why SSE instead of WebSockets?**
SSE is uni-directional (server → client) which is exactly what party broadcast requires. Commands go over regular HTTP so they benefit from standard Spring Security, rate limiting, and request validation without any WebSocket upgrade complexity.

---

## Redis Data Model

### Keys

| Key                               | Type                | TTL     | Purpose                               |
| :-------------------------------- | :------------------ | :------ | :------------------------------------ |
| `party:{partyId}`                 | String (JSON)       | 2 hours | Full party state blob                 |
| `party:user:{userId}:activeParty` | String              | 2 hours | Which party this user is currently in |
| `party:{partyId}:messages`        | List (JSON strings) | 2 hours | Chat message history, capped at 200   |
| `party:{partyId}:events`          | Pub/Sub channel     | —       | Real-time event broadcast channel     |

### Party State Object (`party:{partyId}`)

```json
{
  "partyId": "AB3KF7NZ",
  "hostId": "94310aa8-540b-473d-8c6c-9292366b0fe9",
  "mediaType": "Movie",
  "tmdbId": 550,
  "seasonNo": 0,
  "episodeNo": 0,
  "providerId": "syntherion",
  "progress": 342.0,
  "status": "ACTIVE",
  "createdAt": "2026-05-19T12:50:33.020732Z",
  "endedAt": null,
  "participants": [
    {
      "userId": "94310aa8-540b-473d-8c6c-9292366b0fe9",
      "username": "Arhan Das",
      "avatarUrl": "https://...",
      "host": true,
      "joinedAt": "2026-05-19T12:50:33.020702Z",
      "lastHeartbeat": "2026-05-19T12:55:34.382481Z"
    }
  ]
}
```

> **Note:** `streamUrl` is **never stored** in Redis. It is constructed dynamically from `providerId + tmdbId + progress` at request time via `StreamUrlService`.

### Chat Message Object (`party:{partyId}:messages` list)

```json
{
  "id": "65cf22d9-ae4b-4f3c-b987-ed6ba8b26902",
  "senderId": "94310aa8-540b-473d-8c6c-9292366b0fe9",
  "senderName": "Arhan Das",
  "avatarUrl": "https://...",
  "content": "hello everyone!",
  "sentAt": "2026-05-19T12:30:10.418659Z"
}
```

### SSE Event Envelope (`party:{partyId}:events` channel)

Every event published to Redis Pub/Sub and forwarded to clients has this envelope:

```json
{
  "eventId": "uuid",
  "partyId": "AB3KF7NZ",
  "triggeredById": "94310aa8-...",
  "eventType": "NEW_CHAT",
  "payload": {},
  "occurredAt": "2026-05-19T12:30:10Z"
}
```

---

## REST API Reference

**Base path:** `/api/watchalong/party`  
**Auth:** All endpoints require `Authorization: Bearer <supabase_jwt>` header.

| Method | Path                      | Who         | Description                                                              |
| :----- | :------------------------ | :---------- | :----------------------------------------------------------------------- |
| `POST` | `/create`                 | Anyone      | Create a new party. Caller becomes host.                                 |
| `POST` | `/join`                   | Anyone      | Join an existing party by 8-char code.                                   |
| `POST` | `/leave?partyId={id}`     | Participant | Leave the party. Triggers host migration or termination.                 |
| `POST` | `/progress`               | Host only   | Update playback position and provider. Broadcasts `PARTY_STATE_UPDATED`. |
| `POST` | `/heartbeat?partyId={id}` | Participant | Refresh `lastHeartbeat` to prevent zombie eviction.                      |
| `POST` | `/chat`                   | Participant | Send a chat message. Broadcasts `NEW_CHAT`.                              |
| `GET`  | `/{partyId}/sync`         | Participant | Get current progress + a freshly built `streamUrl`.                      |
| `GET`  | `/{partyId}`              | Participant | Get full party snapshot + last 50 messages.                              |
| `GET`  | `/events?partyId={id}`    | Participant | Open the SSE stream. Returns `text/event-stream`.                        |

---

## Request & Response Shapes

### `POST /create` — Request Body

```json
{
  "mediaType": "Movie",
  "tmdbId": 550,
  "providerId": "syntherion",
  "seasonNo": 0,
  "episodeNo": 0
}
```

- `mediaType`: `"Movie"` | `"TV"` | `"Anime"`
- `seasonNo` / `episodeNo`: Required (> 0) for TV/Anime; send `0` for movies.

### `POST /join` — Request Body

```json
{
  "partyId": "AB3KF7NZ"
}
```

### `POST /progress` — Request Body (Host only)

```json
{
  "partyId": "AB3KF7NZ",
  "progress": 342.5,
  "providerId": "syntherion"
}
```

### `POST /chat` — Request Body

```json
{
  "partyId": "AB3KF7NZ",
  "content": "hello!"
}
```

Content is capped at **500 characters**.

### `PartyStateResponse` — Returned by `/create`, `/join`, `GET /{partyId}`

```json
{
  "partyId": "AB3KF7NZ",
  "hostId": "94310aa8-...",
  "mediaType": "Movie",
  "tmdbId": 550,
  "seasonNo": 0,
  "episodeNo": 0,
  "providerId": "syntherion",
  "streamUrl": "https://provider.example/embed/550?startAt=342",
  "progress": 342.0,
  "status": "ACTIVE",
  "createdAt": "2026-05-19T12:50:33Z",
  "participants": [ { ... } ],
  "recentMessages": [ { ... } ]
}
```

### `SyncResponse` — Returned by `GET /{partyId}/sync`

```json
{
  "progress": 342.0,
  "providerId": "syntherion",
  "streamUrl": "https://provider.example/embed/550?startAt=342"
}
```

---

## SSE Event Stream

### Opening the Stream

```
GET /api/watchalong/party/events?partyId=AB3KF7NZ
Authorization: Bearer <jwt>
Accept: text/event-stream
Cache-Control: no-cache
```

**On success** — HTTP 200 with `Content-Type: text/event-stream`.  
**If party not found** — HTTP 404 JSON: `{ "error": "Party not found: AB3KF7NZ" }`.  
**If not a participant** — HTTP 403 JSON: `{ "error": "You are not a participant in this party." }`.

> The endpoint validates participation **before** creating the SSE emitter, so non-members receive plain JSON errors, not a half-opened stream.

### Wire Format

Spring's `SseEmitter` sends standard SSE frames:

```
id: <eventId-uuid>
event: NEW_CHAT
data: {"eventId":"...","partyId":"AB3KF7NZ","triggeredById":"...","eventType":"NEW_CHAT","payload":{...},"occurredAt":"..."}

```

A blank line terminates each event. The SSE stream has a **2-hour timeout** before the server completes the emitter and the client must reconnect.

### On Connection

Immediately after registration, the server sends a `CONNECTED` frame:

```
event: CONNECTED
data: {"partyId":"AB3KF7NZ","userId":"94310aa8-..."}
```

---

## SSE Event Catalog

### `USER_JOINED`

Fired when a new participant joins.

```json
{
  "payload": {
    "userId": "278a1d2d-...",
    "username": "Arhan Das",
    "avatarUrl": "https://..."
  }
}
```

### `USER_LEFT`

Fired when a participant voluntarily leaves.

```json
{ "payload": { "userId": "278a1d2d-..." } }
```

### `USER_EVICTED`

Fired when the zombie sweeper removes an inactive participant.

```json
{
  "payload": {
    "userId": "278a1d2d-...",
    "reason": "HEARTBEAT_TIMEOUT"
  }
}
```

### `HOST_MIGRATED`

Fired when host duties transfer to another participant.

```json
{
  "payload": {
    "newHostId": "278a1d2d-...",
    "newHostName": "Arhan Das"
  }
}
```

### `PARTY_STATE_UPDATED`

Fired when the host pushes a progress update.

```json
{
  "payload": {
    "progress": 420.0,
    "providerId": "syntherion"
  }
}
```

> This does **not** include a `streamUrl`. Clients that need a fresh playback URL should call `GET /{partyId}/sync`.

### `NEW_CHAT`

Fired when any participant sends a chat message.

```json
{
  "payload": {
    "id": "65cf22d9-...",
    "senderId": "94310aa8-...",
    "senderName": "Arhan Das",
    "avatarUrl": "https://...",
    "content": "hello!",
    "sentAt": "2026-05-19T12:30:10Z"
  }
}
```

### `HEARTBEAT`

Periodic keepalive confirmation.

```json
{ "payload": { "userId": "94310aa8-..." } }
```

### `PARTY_ENDED`

Fired when the last participant leaves or the party is terminated. After broadcasting this event, the server completes all SSE connections for the party.

```json
{ "payload": { "endedAt": "2026-05-19T14:50:33Z" } }
```

---

## Backend Internals

### Party Code Generation

- 8-character code using alphabet `ABCDEFGHJKMNPQRSTUVWXYZ23456789` (excludes visually ambiguous chars: `0/O`, `1/I/L`).
- Generated with `SecureRandom` and validated for uniqueness via Redis `SETNX`. Up to 10 retries before failing.

### Concurrency — Per-Party Locks

`PartyService` holds a `ConcurrentHashMap<String, ReentrantLock>` mapping each `partyId` to a `ReentrantLock`. Any operation that mutates the participant list (join, leave, host migration, zombie eviction) acquires this lock first. This serializes mutations within a single JVM.

> **Production note:** For multi-instance deployments, replace `ReentrantLock` with a distributed lock (e.g. Redisson).

### Auto-Cleanup on Re-entry

Before executing `createParty` or `joinParty`, the service checks `party:user:{userId}:activeParty`. If a value exists, it calls `leaveParty` on the stale party first. If `leaveParty` fails (e.g. stale key points to a deleted party), the tracker key is deleted directly so the user is never stuck.

### Idempotent Join

If a user joins a party they are already listed as a participant in, no mutation occurs. The current party state and last 50 messages are returned immediately. No `USER_JOINED` event is emitted.

### Dynamic Stream URL

`StreamUrlService` is called on every join and sync request:

1. Loads all active providers from PostgreSQL (ordered by priority).
2. Detects if content is anime via TMDB genre/language metadata.
3. Selects the correct URL template (movie / tv / anime).
4. Substitutes `{id}`, `{season}`, `{episode}`, `{startAt}` placeholders with the party's current values.
5. Returns the URL for the matching `providerId`.

If the provider is inactive or not found, an `IllegalStateException` is thrown (wrapped in a `try/catch` in `getPartyState` so the overall response still returns with `streamUrl: null`).

### Zombie Sweeper

`PartyZombieSweeper` runs every **2 minutes** via `@Scheduled(fixedDelay = 120_000)`:

1. Uses Redis `SCAN` with pattern `party:????????` (exactly 8 chars) — never `KEYS *`.
2. For each party: loads state, finds participants whose `lastHeartbeat` is older than **5 minutes**.
3. Acquires the per-party lock, re-reads (to catch heartbeats that arrived between reads).
4. For each confirmed zombie:
   - Removes from participant list.
   - Deletes `party:user:{userId}:activeParty`.
   - Publishes `USER_EVICTED`.
   - If zombie was host → calls `migrateHost`.
   - If party is now empty → calls `endParty`.

### Host Migration

`migrateHost` selects the participant with the earliest `joinedAt` who is not currently the host. It updates `hostId`, sets their `isHost` flag, and publishes `HOST_MIGRATED`.

### Party Termination

`endParty` sets `status = ENDED`, records `endedAt`, saves the state with a **5-minute TTL** (so late joiners get an intelligible error), publishes `PARTY_ENDED`, and removes the per-party lock from the map.

After `PartyEventSubscriber` receives `PARTY_ENDED`, it calls `SseEmitterRegistry.completeAll(partyId)` to gracefully close every SSE connection.

---

## Frontend Integration Guide

### 1. State Variables

```js
const BASE = "/api/watchalong/party";
let currentPartyId = null;
let currentUserId = null; // parsed from JWT payload.sub
let isHost = false;
let sseAbortController = null;
let heartbeatTimer = null;
```

### 2. Auth Headers

```js
function headers() {
  return {
    "Content-Type": "application/json",
    Authorization: `Bearer ${yourJwtToken}`,
  };
}
```

### 3. Create a Party

```js
async function createParty({
  mediaType,
  tmdbId,
  providerId,
  seasonNo = 0,
  episodeNo = 0,
}) {
  const res = await fetch(`${BASE}/create`, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify({
      mediaType,
      tmdbId,
      providerId,
      seasonNo,
      episodeNo,
    }),
  });
  if (!res.ok) throw await res.json();
  const data = await res.json();
  // data is PartyStateResponse
  onPartyJoined(data, true /* isHost */);
}
```

### 4. Join a Party

```js
async function joinParty(partyCode) {
  const res = await fetch(`${BASE}/join`, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify({ partyId: partyCode.toUpperCase() }),
  });
  if (!res.ok) throw await res.json();
  const data = await res.json();
  onPartyJoined(data, false /* isHost */);
}
```

### 5. On Joined — Wire Everything Up

```js
function onPartyJoined(data, host) {
  currentPartyId = data.partyId;
  isHost = host;

  // Render chat history from recentMessages
  data.recentMessages?.forEach((m) => renderChatMessage(m));

  // Load the player with the returned streamUrl
  if (data.streamUrl) loadPlayer(data.streamUrl);

  // Open SSE stream
  connectSSE(data.partyId);

  // Heartbeat every 2.5 min (zombie threshold is 5 min)
  heartbeatTimer = setInterval(sendHeartbeat, 2.5 * 60 * 1000);
}
```

### 6. Open the SSE Stream

Use the Fetch API with a `ReadableStream` reader instead of `EventSource` — this allows passing the `Authorization` header (which `EventSource` does not support).

```js
function connectSSE(partyId) {
  if (sseAbortController) sseAbortController.abort();
  sseAbortController = new AbortController();
  fetchSSE(partyId, sseAbortController.signal);
}

async function fetchSSE(partyId, signal) {
  const res = await fetch(`${BASE}/events?partyId=${partyId}`, {
    signal,
    headers: {
      Authorization: `Bearer ${yourJwtToken}`,
      Accept: "text/event-stream",
      "Cache-Control": "no-cache",
    },
  });

  // If the server returned an error before streaming (404/403), it's JSON
  if (!res.ok) {
    const err = await res.json();
    console.error("SSE open failed", err);
    return;
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let eventName = "message";
  let dataLine = "";

  signal.addEventListener("abort", () => reader.cancel());

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop(); // keep incomplete final line in buffer

    for (const line of lines) {
      const clean = line.trim(); // trim() removes \r from Windows line endings
      if (clean.startsWith("event:")) eventName = clean.slice(6).trim();
      else if (clean.startsWith("data:")) dataLine = clean.slice(5).trim();
      else if (clean === "" && dataLine) {
        handleSSEEvent(eventName, dataLine);
        eventName = "message";
        dataLine = "";
      }
    }
  }
}
```

> **Critical:** Always call `line.trim()` before parsing. Spring SSE may send `\r\n` line endings, and leaving the `\r` causes `event:\r` to not match the `"event:"` prefix, which silently buffers all events.

### 7. Handle SSE Events

```js
function handleSSEEvent(eventName, dataStr) {
  const envelope = JSON.parse(dataStr);
  const payload = envelope.payload;

  switch (eventName) {
    case "CONNECTED":
      console.log("SSE connected", envelope);
      break;

    case "USER_JOINED":
    case "USER_LEFT":
    case "USER_EVICTED":
      refreshParticipantList(); // re-fetch GET /{partyId}
      break;

    case "HOST_MIGRATED":
      // payload: { newHostId, newHostName }
      isHost = payload.newHostId === currentUserId;
      updateHostUI(isHost);
      break;

    case "PARTY_STATE_UPDATED":
      // payload: { progress, providerId }
      // Update your progress display. Do NOT reload the player here —
      // call /sync only if you need a fresh stream URL.
      updateProgressDisplay(payload.progress);
      break;

    case "NEW_CHAT":
      // payload is the full Messages object
      renderChatMessage(payload, payload.senderId === currentUserId);
      break;

    case "PARTY_ENDED":
      onPartyLeft();
      break;

    case "HEARTBEAT":
      // Optional: confirm heartbeat received
      break;
  }
}
```

### 8. Host: Push Progress

The host should call this on a throttled interval (e.g. every 5 seconds) to keep all participants in sync.

```js
async function pushProgress(progress, providerId) {
  if (!isHost || !currentPartyId) return;
  await fetch(`${BASE}/progress`, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify({ partyId: currentPartyId, progress, providerId }),
  });
}
```

### 9. Sync — Get a Fresh Stream URL

Call this when a participant joins mid-party or needs to resync their player.

```js
async function syncPlayer() {
  const res = await fetch(`${BASE}/${currentPartyId}/sync`, {
    headers: headers(),
  });
  const { progress, providerId, streamUrl } = await res.json();
  if (streamUrl) loadPlayer(streamUrl);
}
```

### 10. Send Chat

```js
async function sendChat(content) {
  if (!content.trim() || !currentPartyId) return;
  await fetch(`${BASE}/chat`, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify({ partyId: currentPartyId, content }),
  });
  // Do NOT append the message locally — wait for the NEW_CHAT SSE event
  // so all clients (including sender) render from the same source of truth.
}
```

### 11. Leave the Party

```js
async function leaveParty() {
  if (!currentPartyId) return;
  try {
    await fetch(`${BASE}/leave?partyId=${currentPartyId}`, {
      method: "POST",
      headers: headers(),
    });
  } finally {
    onPartyLeft(); // always clean up locally even if the request fails
  }
}

function onPartyLeft() {
  currentPartyId = null;
  isHost = false;
  if (sseAbortController) {
    sseAbortController.abort();
    sseAbortController = null;
  }
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
  // Reset UI to pre-join state
}
```

### 12. Heartbeat

Runs automatically on the interval started in `onPartyJoined`.

```js
async function sendHeartbeat() {
  if (!currentPartyId) return;
  await fetch(`${BASE}/heartbeat?partyId=${currentPartyId}`, {
    method: "POST",
    headers: headers(),
  });
}
```

---

## Full Frontend Reference Implementation

Below is the minimal but complete reference implementation extracted from `party-test.js`.

```js
const BASE = "/api/watchalong/party";
let currentPartyId = null;
let currentUserId = null;
let isHost = false;
let sseAbortController = null;
let heartbeatTimer = null;

// ── Auth ──────────────────────────────────────────────────────────────────────

function parseToken(jwt) {
  const payload = JSON.parse(atob(jwt.split(".")[1]));
  currentUserId = payload.sub;
}

function headers(jwt) {
  return { "Content-Type": "application/json", Authorization: `Bearer ${jwt}` };
}

// ── API Calls ─────────────────────────────────────────────────────────────────

async function createParty(
  jwt,
  { mediaType, tmdbId, providerId, seasonNo = 0, episodeNo = 0 },
) {
  const res = await fetch(`${BASE}/create`, {
    method: "POST",
    headers: headers(jwt),
    body: JSON.stringify({
      mediaType,
      tmdbId,
      providerId,
      seasonNo,
      episodeNo,
    }),
  });
  const data = await res.json();
  if (!res.ok) throw data;
  onPartyJoined(jwt, data, true);
}

async function joinParty(jwt, code) {
  const res = await fetch(`${BASE}/join`, {
    method: "POST",
    headers: headers(jwt),
    body: JSON.stringify({ partyId: code.toUpperCase() }),
  });
  const data = await res.json();
  if (!res.ok) throw data;
  onPartyJoined(jwt, data, false);
}

async function leaveParty(jwt) {
  if (!currentPartyId) return;
  try {
    await fetch(`${BASE}/leave?partyId=${currentPartyId}`, {
      method: "POST",
      headers: headers(jwt),
    });
  } finally {
    onPartyLeft();
  }
}

async function pushProgress(jwt, progress, providerId) {
  if (!isHost || !currentPartyId) return;
  await fetch(`${BASE}/progress`, {
    method: "POST",
    headers: headers(jwt),
    body: JSON.stringify({ partyId: currentPartyId, progress, providerId }),
  });
}

async function sendHeartbeat(jwt) {
  if (!currentPartyId) return;
  await fetch(`${BASE}/heartbeat?partyId=${currentPartyId}`, {
    method: "POST",
    headers: headers(jwt),
  });
}

async function sendChat(jwt, content) {
  if (!content.trim() || !currentPartyId) return;
  await fetch(`${BASE}/chat`, {
    method: "POST",
    headers: headers(jwt),
    body: JSON.stringify({ partyId: currentPartyId, content }),
  });
}

async function syncPlayer(jwt) {
  const res = await fetch(`${BASE}/${currentPartyId}/sync`, {
    headers: headers(jwt),
  });
  const data = await res.json();
  return data; // { progress, providerId, streamUrl }
}

// ── SSE Stream ────────────────────────────────────────────────────────────────

function connectSSE(partyId, jwt) {
  if (sseAbortController) {
    sseAbortController.abort();
  }
  sseAbortController = new AbortController();
  fetchSSE(partyId, jwt, sseAbortController.signal);
}

async function fetchSSE(partyId, jwt, signal) {
  try {
    const res = await fetch(`${BASE}/events?partyId=${partyId}`, {
      signal,
      headers: {
        Authorization: `Bearer ${jwt}`,
        Accept: "text/event-stream",
        "Cache-Control": "no-cache",
      },
    });
    if (!res.ok) {
      console.error("SSE open error", res.status);
      return;
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    let eventName = "message";
    let dataLine = "";

    signal.addEventListener("abort", () => reader.cancel());

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop();

      for (const line of lines) {
        const clean = line.trim();
        if (clean.startsWith("event:")) eventName = clean.slice(6).trim();
        else if (clean.startsWith("data:")) dataLine = clean.slice(5).trim();
        else if (clean === "" && dataLine) {
          handleSSEEvent(eventName, dataLine);
          eventName = "message";
          dataLine = "";
        }
      }
    }
  } catch (e) {
    if (e.name !== "AbortError") console.error("SSE error", e);
  }
}

// ── Event Handling ────────────────────────────────────────────────────────────

function handleSSEEvent(eventName, dataStr) {
  const envelope = JSON.parse(dataStr);
  const payload = envelope.payload;

  switch (eventName) {
    case "NEW_CHAT":
      renderChatMessage(payload, payload.senderId === currentUserId);
      break;
    case "PARTY_STATE_UPDATED":
      console.log("Progress update:", payload.progress, payload.providerId);
      break;
    case "HOST_MIGRATED":
      isHost = payload.newHostId === currentUserId;
      break;
    case "USER_JOINED":
    case "USER_LEFT":
    case "USER_EVICTED":
      // refresh participant roster from server
      break;
    case "PARTY_ENDED":
      onPartyLeft();
      break;
  }
}

// ── Lifecycle ─────────────────────────────────────────────────────────────────

function onPartyJoined(jwt, data, host) {
  currentPartyId = data.partyId;
  isHost = host;
  connectSSE(data.partyId, jwt);
  heartbeatTimer = setInterval(() => sendHeartbeat(jwt), 2.5 * 60 * 1000);
  // Render data.recentMessages, load data.streamUrl into player, etc.
}

function onPartyLeft() {
  currentPartyId = null;
  isHost = false;
  if (sseAbortController) {
    sseAbortController.abort();
    sseAbortController = null;
  }
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
}
```

---

## Concurrency & Safety Notes

| Concern                    | Implementation                                                                                   |
| :------------------------- | :----------------------------------------------------------------------------------------------- |
| Participant list mutations | Per-party `ReentrantLock` (JVM-local)                                                            |
| Party code collisions      | Redis `SETNX` on `party:{code}` — atomic, up to 10 retries                                       |
| Ghost sessions             | `party:user:{userId}:activeParty` checked on every create/join                                   |
| Stale stream URLs          | Never stored — built on demand via `StreamUrlService`                                            |
| Zombie participants        | `@Scheduled` sweeper every 2 min, 5-min heartbeat timeout                                        |
| Redis scan safety          | `SCAN` with pattern — never `KEYS *`                                                             |
| SSE dead connections       | `SseEmitterRegistry` catches `IOException` on send and removes the emitter                       |
| Party ended state          | Ended parties TTL to 5 min — `getActiveParty` throws `NoSuchElementException` for `ENDED` status |
| Multi-node readiness       | Pub/Sub fan-out via `RedisMessageListenerContainer` with `PatternTopic("party:*:events")`        |
