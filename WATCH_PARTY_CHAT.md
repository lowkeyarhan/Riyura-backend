# Watch Party Chat — Frontend Integration

Everything the frontend needs to make chat behave like a WhatsApp group: instant
echo for the sender, no duplicates, and no missed messages after a reconnect.

**Scope:** `POST /chat` and `GET /events` in the watch-party module. The backend
work is done and tested; the remaining work is all client-side.

---

## 1. Context: what was broken

| #   | Root cause                                                                                                                     | Symptom                                                                              |
| --- | ------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------ |
| 1   | SSE connections were keyed by `(partyId, userId)`. A dead connection's cleanup removed the newer connection for the same user. | A user reconnects, then stops receiving chat.                                        |
| 2   | `POST /chat` returned `204 No Content`; the sender's bubble was rendered only from the async `NEW_CHAT` SSE echo.              | Sender's message lags or never appears.                                              |
| 3   | No `Last-Event-ID`, no history on connect, no client reconnect.                                                                | Any disconnect (proxy idle-kill, laptop sleep, 2 h timeout) loses messages silently. |
| 4   | One `SseEventBuilder` was reused for every recipient. Spring's builder accumulates state.                                      | Extra blank frames and unbounded growth on busy parties.                             |
| 5   | Every send generated a fresh UUID; retries wrote a second message.                                                             | Duplicate bubbles and duplicate history.                                             |

## 2. What the backend does now

- `POST /chat` stores the message **once**, returns the stored message with `200`,
  and broadcasts it to every connected participant.
- The message `id` is stable everywhere: POST response, `NEW_CHAT` payload,
  `recentMessages` history. Use it as the dedupe key.
- `CONNECTED` carries `recentMessages` so a reconnecting client can reconcile.
- The server pings the stream every 15 s and sends `Cache-Control: no-transform`
  and `X-Accel-Buffering: no`.
- `clientMessageId` is an optional idempotency key: same key = same message, no
  second store, no second history entry.

> The SSE wire `id:` field is a random **event** id, not the message id. Do not
> dedupe on it; dedupe on `payload.id`.

## 3. API contract

Base: `/api/watchalong/party`. Auth: `Authorization: Bearer <jwt>` on every call.

### Send a message

```
POST /chat
Content-Type: application/json

{
  "partyId": "ABCD1234",
  "content": "hello",
  "clientMessageId": "5f0c1d1e-..."   // optional but recommended
}
```

`partyId` is the 8-char code; the server uppercases it.

Responses:

- `200` — the stored message:

```json
{
  "id": "5f0c1d1e-...",
  "senderId": "uuid",
  "senderName": "Robin",
  "avatarUrl": "https://...",
  "content": "hello",
  "sentAt": "2026-11-16T18:22:13.511Z"
}
```

- `400` — validation (`content` blank or > 500 chars, `partyId` not 8 chars).
- `403` — caller is not a participant.
- `404` — party missing or ended.

A retry with the same `clientMessageId` returns the **same** message (same `id`,
same `sentAt`), so a retry is a no-op.

### Open the stream

```
GET /events?partyId=ABCD1234
Accept: text/event-stream
```

- `404` / `403` come back as **JSON**, not a stream. Handle before starting the reader.
- The stream stays open for up to 2 hours. The client must reconnect.

## 4. SSE events

Every event is named and carries the full envelope as JSON:

```json
{
  "eventId": "uuid",
  "partyId": "ABCD1234",
  "triggeredById": "uuid",
  "eventType": "NEW_CHAT",
  "payload": { ... },
  "occurredAt": "2026-11-16T18:22:13.511Z"
}
```

| Event                 | `payload`                                                  |
| --------------------- | ---------------------------------------------------------- |
| `CONNECTED`           | `{ partyId, userId, recentMessages: Message[] }`           |
| `NEW_CHAT`            | `{ id, senderId, senderName, avatarUrl, content, sentAt }` |
| `USER_JOINED`         | `{ userId, username, avatarUrl }`                          |
| `USER_LEFT`           | `{ userId }`                                               |
| `USER_EVICTED`        | `{ userId, reason }`                                       |
| `HOST_MIGRATED`       | `{ newHostId, newHostName }`                               |
| `PARTY_STATE_UPDATED` | `{ progress, providerId }`                                 |
| `PARTY_ENDED`         | `{ endedAt }`                                              |
| `HEARTBEAT`           | `{ userId }`                                               |

`recentMessages` is the last 50 messages, oldest first, and never `null`.

## 5. Frontend work to do

### Must do

1. **Generate the idempotency key once per message.** Create `clientMessageId`
   when the user hits send and reuse the same value on retry (network error,
   timeout, 5xx). Do not regenerate inside a retry loop.

2. **Render from the POST response.** On `200`, append the returned message
   immediately, then ignore the later `NEW_CHAT` echo because it has the same `id`.

3. **Dedupe the whole chat UI by `message.id`.** One `Set<string>` must cover all
   three sources: POST response, `NEW_CHAT` payload, and `recentMessages` from
   `CONNECTED` (and from `join` / `GET /party/{partyId}`). Without this, a
   reconnect renders history twice.

4. **Handle `CONNECTED`.** Merge `payload.recentMessages` into the list; do not
   replace it, and do not clear the transcript.

5. **Reconnect with backoff.** Reconnect when the reader ends, the fetch throws,
   or the 2 h timeout fires. Stop only when the user leaves the party.
   Reconnecting is safe now: `CONNECTED` replays history and id-dedupe removes
   overlaps.

6. **Do not use the SSE frame `id:` for dedupe.** It is random per publish.

### Reference implementation

```js
const seen = new Set(); // message ids already rendered
const rendered = []; // message list, oldest first

function render(msg) {
  if (!msg?.id || seen.has(msg.id)) return;
  seen.add(msg.id);
  rendered.push(msg);
  paintChat(rendered); // or append one bubble
}

async function send(content) {
  const clientMessageId = crypto.randomUUID(); // once per message
  const res = await postChat({ partyId, content, clientMessageId });
  if (res.ok) render(await res.json()); // instant echo for the sender
}

const es = new EventSource(urlWithToken); // or fetch + ReadableStream
es.addEventListener("NEW_CHAT", (e) => render(JSON.parse(e.data).payload));
es.addEventListener("CONNECTED", (e) => {
  const { recentMessages } = JSON.parse(e.data).payload;
  recentMessages.forEach(render); // merge, never replace
});
```

`EventSource` cannot send an `Authorization` header. Use the existing
`fetch` + `ReadableStream` approach, or pass the token another way.

### Do not do

- Do not append the message twice (POST response + echo) without the id check.
- Do not cache or transform the SSE response; compression and buffering break
  live delivery.
- Do not keep a stream open past 2 h without expecting a reconnect.
- Do not assume `avatarUrl` exists; it can be `null` on messages and `""` on
  `USER_JOINED`.
- Do not insert `content` as raw HTML. Message text is user input; render it as
  text (or sanitize) to avoid XSS.

## 6. Known limits (documented, not bugs)

- History window is **50 messages** in `CONNECTED` and in `GET /party/{partyId}`.
  A client that misses more than 50 during a long disconnect cannot fetch the
  older ones (the server keeps 200). Add pagination only if this matters.
- Redis Pub/Sub is fire-and-forget. If a broadcast is lost, the message is still
  stored and arrives on the next reconnect via `CONNECTED`.
- A single slow client can delay the shared fan-out thread. Acceptable at party
  scale (max 20 participants); revisit before large rooms.
- Party writes are serialized per JVM. Running more than one backend instance
  needs a distributed lock before relying on it.

## 7. Testing the contract by hand

`src/main/resources/static/party-test.js` is a working reference client
(create/join, stream, chat, dedupe, reconnect). Use it to verify behaviour
before touching a real client.
