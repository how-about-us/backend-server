# Mongo Chat Design

## Goal

Implement room chat over STOMP while storing chat messages in MongoDB.

## Decisions

- Chat messages are stored in MongoDB because message metadata can vary by message type and this project uses MongoDB as a learning target.
- PostgreSQL remains the source of truth for rooms, users, and room membership authorization.
- Clients send chat commands to `/app/rooms/{roomId}/messages`.
- Successful sends are persisted first, then broadcast to `/topic/rooms/{roomId}/messages`.
- Failed sends are delivered only to the sender through `/user/queue/errors`.
- The client provides `clientMessageId` for optimistic UI correlation. The MVP does not persist it for idempotency.

## Message Shape

MongoDB `messages` documents contain:

- `_id`
- `roomId`
- `senderId`
- `messageType`
- `content`
- `metadata`
- `createdAt`

The API exposes `_id` as `id`.

## Error Shape

User queue errors contain:

- `domain`: `MESSAGE`
- `action`: `SEND`
- `clientRequestId`: the request `clientMessageId`
- `code`
- `message`
- `retryable`

## Sync

MongoDB ObjectId strings are exposed as message cursors. The first implementation returns recent room messages ordered by `createdAt desc` and `_id desc`, and supports `afterId` for messages created after the last received message.
