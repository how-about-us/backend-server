# Room Realtime Connection Design

## Goal

When a user opens a room, the room page should load through REST first, while realtime features connect separately through SockJS + STOMP. A temporary WebSocket failure must not block the user from viewing the room.

## Agreed Flow

1. The client requests the room's initial data with `GET /rooms/{roomId}`.
2. The client opens a SockJS endpoint such as `/ws`.
3. Browser cookies are sent on the SockJS/WebSocket HTTP handshake and fallback HTTP transport requests.
4. The server authenticates the HTTP-only `access_token` cookie during the handshake and stores the authenticated user id on the WebSocket session.
5. The client sends a STOMP `CONNECT` frame.
6. The server accepts `CONNECT` only when the WebSocket session has an authenticated user id.
7. The client subscribes to a room destination.
8. The server checks that the authenticated user is a `HOST` or `MEMBER` of the room before allowing the subscription.
9. After a successful room subscription, the server records the user as online in Redis and broadcasts the room presence event.

## Authentication Boundary

Cookies are not added to the STOMP `CONNECT` frame itself. The cookie belongs to the initial HTTP handshake or SockJS fallback HTTP requests. STOMP frames then reuse the authenticated identity attached to the WebSocket session.

This keeps the current HTTP-only cookie strategy intact and avoids exposing the access token to Next.js client-side JavaScript.

## Authorization Boundary

- `CONNECT`: verifies that the socket session belongs to an authenticated user.
- `SUBSCRIBE`: verifies that the authenticated user can access the requested room.
- `DISCONNECT`: removes the user's online presence for the subscribed room sessions.

## SockJS Considerations

SockJS creates more than one URL under the endpoint prefix, for example `/ws/info` and transport-specific paths under `/ws/**`. Security and CORS rules must account for the endpoint prefix, while STOMP interceptors enforce authentication and room authorization.

## Failure Handling

The room page remains available after REST room detail succeeds. If realtime connection fails, the client should show realtime as disconnected or reconnecting and retry. The server must not write Redis online state or broadcast presence until room subscription succeeds.

Failures should be separated as follows:

- Missing or invalid token: reject STOMP connection.
- Non-member or pending member: reject room subscription.
- Network or transient SockJS failure: allow client retry without changing room membership state.

## Redis Presence Keys

- `room:{roomId}:connected_users`: set of online user ids for the room.
- `room:{roomId}:sessions:{userId}`: set of active WebSocket/STOMP session ids for that user in that room.

The session key prevents a user from being marked offline when they close one of several active tabs or browser sessions.

## Documentation Impact

This feature updates `docs/ai/features.md` because it implements realtime room presence tracking. It updates `docs/ai/erd.md` only for Redis key documentation and does not require a database schema change.
