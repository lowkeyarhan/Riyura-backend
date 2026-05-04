# Watch Party Frontend Implementation Guide

This document serves as a comprehensive prompt and implementation guide for frontend developers building the Watch Party feature. It covers all REST endpoints, WebSocket channels, payloads, event types, and architectural considerations required to synchronize media playback and chat across multiple clients.

---

## 1. Overview

The Watch Party feature allows multiple users to join a synchronized media session. One user acts as the **Host**, controlling the primary playback state (seek, pause, resume), while others act as **Participants**.

The system relies on:

1. **REST APIs** for session creation and fetching the initial state.
2. **WebSockets (SockJS + STOMP)** for real-time bidirectional communication (chat, playback sync, participant presence).

---

## 2. REST API Integration

### 2.1. Create a Party (Host Only)

Use this endpoint when a user starts a watch party. The user who creates the party is automatically assigned as the Host.

- **Endpoint:** `POST /api/party/create`
- **Headers:** `Authorization: Bearer <jwt-token>`
- **Payload:**
  ```json
  {
    "tmdbId": 12345,
    "mediaType": "Movie", // Must be "Movie" or "TV"
    "seasonNo": 0, // 0 if Movie
    "episodeNo": 0, // 0 if Movie
    "providerId": "vidsrc",
    "startAt": 0 // Playback starting point in seconds
  }
  ```
- **Response:**
  ```json
  {
    "success": true,
    "partyId": "uuid-string-here"
  }
  ```

### 2.2. Fetch Party State

Use this endpoint when a user joins an existing party to load the initial metadata, participant list, and recent chat history.

- **Endpoint:** `GET /api/party/{partyId}/state`
- **Headers:** `Authorization: Bearer <jwt-token>`
- **Response:**
  ```json
  {
    "success": true,
    "data": {
      "partyId": "uuid",
      "hostId": "user-uuid",
      "tmdbId": 12345,
      "mediaType": "Movie",
      "seasonNo": 0,
      "episodeNo": 0,
      "providerId": "vidsrc",
      "startAt": 120, // Current position in seconds
      "partyStartedAt": 1672531200000,
      "strictSync": false,
      "participantIds": ["user-1", "user-2"],
      "recentChat": [
        {
          "senderId": "user-1",
          "senderDisplayName": "John",
          "senderProfilePhoto": "url",
          "text": "Hello!",
          "serverTime": 1672531205000
        }
      ],
      "serverTime": 1672531210000
    }
  }
  ```

---

## 3. WebSocket Integration (SockJS + STOMP)

The frontend should connect to the WebSocket server using SockJS and STOMP immediately after fetching the party state.

### 3.1. Connection Details

- **WebSocket URL:** `/ws` (e.g., `https://api.yourdomain.com/ws`)
- **Headers for STOMP connect:** `Authorization: Bearer <jwt-token>`

```javascript
// Example Connection Code
const socket = new SockJS("https://api.yourdomain.com/ws");
const stompClient = Stomp.over(socket);

stompClient.connect({ Authorization: "Bearer " + jwtToken }, () => {
  console.log("Connected to WebSocket");
  // Setup Subscriptions...
  // Send Join Command...
});
```

### 3.2. Subscriptions (Listening for Events)

The frontend needs to subscribe to two main destinations:

#### A. Public Party Topic (Broadcasts)

- **Destination:** `/topic/party/{partyId}`
- **Purpose:** Receives events that affect all users in the party (chat, global syncs, pauses, user joins/leaves).

#### B. Directed User Queue (Private)

- **Destination:** `/user/queue/sync` and `/user/queue/heartbeat-ack`
- **Purpose:** Receives private system messages directed only to the current user (e.g., auto-sync on join, manual sync request responses).

---

## 4. WebSocket Actions (Sending Messages)

All outgoing WebSocket messages should be sent to `/app/party/{partyId}/{action}`.

### 4.1. Join Party

Send immediately after establishing the WebSocket connection.

- **Destination:** `/app/party/{partyId}/join`
- **Payload:** `{}`
- **Triggers:** System broadcasts `USER_JOINED` to the topic and sends a private `SYNC` to the joining user.

### 4.2. Push Sync (Host usually, or anyone if Strict Sync is off)

Triggered when the host seeks, plays, or pauses.

- **Destination:** `/app/party/{partyId}/sync`
- **Payload:**
  ```json
  {
    "startAt": 125.5, // New playback position in seconds
    "clientTime": 1672531200000, // Date.now()
    "action": "SEEK" // Or PLAY / PAUSE
  }
  ```
- **Triggers:** System broadcasts `SYNC` to all users.

### 4.3. Request Sync (Participant)

Failsafe button for participants to say "sync me to the host now".

- **Destination:** `/app/party/{partyId}/request-sync`
- **Payload:** `{}`
- **Triggers:** System sends a private `SYNC` to the requester.

### 4.4. Chat Message

- **Destination:** `/app/party/{partyId}/chat`
- **Payload:** `{ "text": "Hello everyone!" }`
- **Triggers:** System broadcasts `CHAT` to all users.

### 4.5. Buffering State Management

Used to pause the video for everyone if a participant's internet is slow.

- **Start Buffering:** Send `{}` to `/app/party/{partyId}/buffering`
  _(Triggers `FORCE_PAUSE` broadcast if threshold met)_
- **End Buffering:** Send `{}` to `/app/party/{partyId}/buffering-complete`
  _(Triggers `RESUME` broadcast when everyone is ready)_

### 4.6. Toggle Strict Sync (Host Only)

Prevents participants from altering the playback state.

- **Destination:** `/app/party/{partyId}/toggle-strict-sync`
- **Payload:** `{}`
- **Triggers:** System broadcasts `STRICT_SYNC_TOGGLED`.

### 4.7. Heartbeat (Crucial for Connection Management)

Send every 5 seconds to prevent the server from kicking the user out as a "zombie".

- **Destination:** `/app/party/{partyId}/heartbeat-ws`
- **Payload:** `{}`
- **Triggers:** System sends a private `HEARTBEAT_ACK` and resets the user's timeout.

---

## 5. Handling Incoming Server Events

Messages received on the subscriptions will have the following structure:

```json
{
  "event": "EVENT_NAME",
  "payload": { ... },
  "senderId": "uuid",
  "timestamp": 1672531200000
}
```

### Event Dictionary

Handle these events in your frontend state/reducer:

- **`USER_JOINED`**: A new user entered. Payload contains `userId`, `userName`, and updated `participantIds` array.
- **`USER_LEFT`**: A user left or timed out. Update the participant list.
- **`NEW_HOST_ASSIGNED`**: The previous host left, and the server assigned a new host. Update the UI to show host controls if `payload.hostId === myUserId`.
- **`CHAT`**: A new chat message. Append `payload.text` to the chat window.
- **`SYNC`**: Playback sync command. Payload contains `startAt` and `partyStartedAt`. Update your video player's current time.
- **`FORCE_PAUSE`**: Video must be paused (e.g., someone is buffering).
- **`RESUME`**: Video can resume playing (e.g., buffering resolved).
- **`STRICT_SYNC_TOGGLED`**: Payload contains `strictSync` boolean. If true, hide seek bars for non-hosts.
- **`HEARTBEAT_ACK`**: System acknowledgment of your heartbeat.
- **`PARTY_CLOSED`**: The party ended. Redirect the user out of the room.
- **`ERROR`**: Something went wrong. Display an alert based on payload.

---

## 6. Implementation Checklist & Best Practices

1. **Connection Resiliency:**
   - If the WebSocket disconnects unexpectedly, attempt reconnection with exponential backoff.
   - Restart the 5-second heartbeat interval upon successful reconnection.
2. **Video Player Wrapper:**
   - Intercept the video player's `onSeeked`, `onPlay`, and `onPause` events.
   - If the user is the Host (or Strict Sync is off), emit the `sync` WebSocket action.
   - If the user is a Participant (and Strict Sync is on), `preventDefault()` their actions and force the player back to the server's synced time.
3. **Latency Compensation:**
   - When receiving a `SYNC` event, account for network latency by comparing the event's `timestamp` to `Date.now()`.
4. **Buffering Threshold:**
   - Listen to the video player's `onWaiting` (buffering) and `onPlaying` (buffering resolved) events to trigger the `/buffering` and `/buffering-complete` endpoints respectively.
5. **UI Structure:**
   - Split view: Media Player on the left/main area, collapsible Sidebar on the right.
   - Sidebar contains: Chat tab, Participant List tab, and Host Controls tab (if user is host).
