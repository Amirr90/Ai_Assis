# Media Message Handling Flow

This flow prevents poor UX by avoiding AI calls when incoming notifications are media placeholders (for example: `Sent a reel`, `📹 Video`, `Photo`).

```mermaid
flowchart TD
    notificationReceived["NotificationReceived<br/>android.text / android.title / android.subText"]
    messageTypeDetector[MessageTypeDetector]
    textPath[MessageType = TEXT]
    mediaPath[MessageType = MEDIA]

    buildContext[BuildConversationContext]
    hybridUseCase[GetHybridSuggestionsUseCase]
    aiSuggestions[OnDeviceOrCloudSuggestions]
    showSuggestionChips[ShowSuggestionChips]

    mediaReplyProvider[MediaReplyProvider<br/>PredefinedReplies]
    showQuickReplyChips[ShowQuickReplyChips]

    noAiForMedia["DoNotCallAIForMediaPlaceholders<br/>Sent a reel / 📹 Video / Photo"]

    notificationReceived --> messageTypeDetector
    messageTypeDetector --> textPath
    messageTypeDetector --> mediaPath

    textPath --> buildContext
    buildContext --> hybridUseCase
    hybridUseCase --> aiSuggestions
    aiSuggestions --> showSuggestionChips

    mediaPath --> mediaReplyProvider
    mediaReplyProvider --> showQuickReplyChips
    mediaPath --> noAiForMedia
```

## Notes
- TEXT messages continue through the existing hybrid AI pipeline.
- MEDIA messages skip AI completely and use curated reply sets.
- This saves token cost and avoids irrelevant/random suggestions.
