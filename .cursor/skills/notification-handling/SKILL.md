---
name: notification-handling
description: >-
  Parses Android notification payloads into a normalized model, filters duplicate
  notifications using stable dedupe keys and time windows, and handles WhatsApp
  and Instagram edge cases such as group messages, reactions, and summary/bundled
  notifications. Use when building or fixing NotificationListenerService flows,
  deduping notification streams, or app-specific parsing for WhatsApp/Instagram.
disable-model-invocation: true
---

# Notification Handling

Use this skill when implementing production-ready notification ingestion on Android (Kotlin, Coroutines, MVVM, Clean Architecture).

## Goals

- Parse raw `StatusBarNotification` data into a consistent domain model.
- Filter duplicates without dropping genuinely new messages.
- Handle WhatsApp and Instagram package-specific edge cases.
- Keep parsing and dedupe logic in domain/data layers, not in Composables.

## Normalized model

Create one normalized model and map all app payloads into it:

```kotlin
data class ParsedNotification(
    val sourceApp: String,
    val packageName: String,
    val conversationId: String?,
    val sender: String?,
    val messageText: String?,
    val timestampMs: Long,
    val postedAtMs: Long,
    val notificationKey: String?,
    val groupKey: String?,
    val isGroupConversation: Boolean,
    val messageType: MessageType
)
```

Keep source extras in a separate raw object only when needed for debugging.

## Parsing workflow

1. Read common fields from `Notification.extras`:
   - `Notification.EXTRA_TITLE`
   - `Notification.EXTRA_TEXT`
   - `Notification.EXTRA_BIG_TEXT`
   - `Notification.EXTRA_SUB_TEXT`
   - `Notification.EXTRA_MESSAGES` (for messaging style)
2. Prefer messaging-style messages when present; fallback to title/text.
3. Resolve `conversationId` from the strongest available identifier:
   - Explicit conversation shortcut/channel metadata
   - Group key / thread key in extras
   - Derived fallback from package + sender/title
4. Normalize text:
   - Trim whitespace
   - Collapse repeated spaces/newlines
   - Reject empty/non-informative payloads
5. Emit immutable `ParsedNotification` via repository/use-case.

## Duplicate filtering strategy

Use layered dedupe, in this order:

1. **Strong key dedupe**
   - Key: `notificationKey` when stable and present.
   - Skip if already seen in recent cache.
2. **Content fingerprint dedupe**
   - Build fingerprint from:
     - `packageName`
     - `conversationId` (or normalized sender)
     - normalized `messageText`
     - bucketed timestamp (e.g., 2-5 second bucket)
   - Hash and compare against LRU/TTL cache.
3. **Burst-window suppression**
   - For same conversation + same text in short window (e.g., 3-8 sec), keep first only.

Recommended defaults:
- In-memory LRU (2000-5000 entries) + TTL (5-15 minutes).
- Thread-safe access via `Mutex`.
- Optional persisted cache for process restarts if duplicates are costly.

## WhatsApp edge cases (`com.whatsapp`, `com.whatsapp.w4b`)

- Ignore purely summary/bundled notifications that do not carry a new message.
- Group chats:
  - Distinguish sender vs group title.
  - Do not use group title alone as dedupe identity.
- Reactions and edited/deleted events:
  - Tag as non-message event types when recognizable.
  - Avoid treating them as brand-new text messages.
- Multi-device sync can replay similar payloads:
  - Depend on fingerprint + burst suppression, not title only.

## Instagram edge cases (`com.instagram.android`)

- Distinguish DM message notifications from activity notifications (likes/follows/live).
- Repeated activity notifications often have similar text:
  - Use tighter burst windows for activity type.
- Message requests/general inbox can present similar title/text:
  - Include conversation hint/thread metadata when available.
- Avoid duplicates caused by summary + child notifications arriving together.

## Architecture guidance (MVVM/Clean)

- `NotificationListenerService` only captures raw payload and forwards it.
- Parsing + dedupe live in data/domain layer (`NotificationParser`, `DedupeEngine`, use case).
- ViewModel collects final `Flow<List<ParsedNotification>>`.
- UI layer displays state only; no business rules in Compose.

## Validation checklist

- [ ] Same notification posted twice is emitted once.
- [ ] Two distinct messages with similar text are both retained.
- [ ] WhatsApp group and direct chats are correctly separated.
- [ ] Instagram DM and activity notifications are classified correctly.
- [ ] Burst duplicates are suppressed without hiding real new events.

