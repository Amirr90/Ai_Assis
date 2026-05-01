# AI Assis - Feature Inventory and Recommendations

This document lists the features currently available in the project, grouped feature-wise, and suggests what to build next with practical implementation approaches.

## 1) Implemented Features (Feature-wise)

### A. Onboarding, Permissions, and App Entry
- Permission onboarding flow for required capabilities.
- Continue/Skip-style permission progression.
- Privacy policy link from onboarding.
- Permission-gated app start behavior.

### B. Dashboard / Home Experience
- Assistant status and permission status display.
- AI toggle (enable/disable suggestions).
- Tone selection controls.
- Reply length selection.
- Custom template shortcuts.
- Navigation entry points for App Filter and Suggestions.
- Chat history section with actions:
  - Copy reply
  - Send reply
  - Reply later

### C. Suggestions UI and Generation Triggering
- Dedicated Suggestions screen route is wired and active.
- Tone chips and suggestion list rendering.
- Loading and error state handling in suggestions UI.
- Copy interaction from generated suggestions.

### D. Overlay Assistant (Realtime UX)
- Foreground overlay service.
- Draggable chat-bubble behavior.
- Expandable panel mode.
- Unread badge indicator.
- Pause/resume assistant controls.
- Clear/collapse actions.
- Edit-before-copy/send behavior.
- Retry handling for failed generation.
- Tap-outside dismiss with scrim.
- Bubble edge snap and saved position.

### E. Notification Ingestion and Filtering
- NotificationListener-based message ingestion.
- Per-app monitoring filter support.
- Message extraction from notification payloads.
- Duplicate suppression.
- Self-message echo suppression.
- Summary notification detection and policy handling.
- Business-message detection and selective skipping.

### F. Reply Execution
- Direct reply via `RemoteInput` when available.
- Clipboard fallback when direct reply is unavailable.
- Registry support for direct-reply actions.

### G. AI Suggestion Pipeline (Core Engine)
- Hybrid generation orchestration:
  - On-device first
  - Cloud fallback
- Timeout and fallback reason handling.
- Circuit-breaker style reliability guard.
- Suggestion caching support.
- Conversation context building.
- Context memory use case.
- Reply policy resolution per app/context.

### H. On-device Intelligence and Language Support
- On-device pattern-based suggestions.
- Multi-language orientation (English/Hindi/Hinglish handling).
- Style-memory related local data source hooks.
- Media-aware quick reply provider path.

### I. Remote AI and Networking
- Ktor-based remote API calls.
- OpenAI service integration path.
- Gemini/alternate provider service integration path.
- Remote response parsing and fallback behavior.

### J. Preferences, Persistence, and Architecture Foundation
- DataStore-backed feature preferences.
- DataStore-backed tone preferences.
- DataStore-backed monitored app settings.
- Hilt dependency injection setup.
- MVVM structure with ViewModels and UI state flows.

### K. Input Method (Keyboard) and Reminders
- IME service that shows latest AI suggestions.
- Insert selected suggestion into active input field.
- Reply-later scheduling path using worker.

## 2) Partially Implemented / In Progress Areas

- Post-MVP card items exist in UI but not all end-to-end flows are complete.
- Voice-related support is partially available, but a complete voice-reply generation flow is not fully productized.
- Reply-later is available as scheduling, but full management UX (history/snooze/cancel flow) is limited.
- Some documentation appears stale compared to actual implemented routes/features.
- IME works, but the keyboard layer is not fully aligned to a Compose-first UI approach.

## 3) Recommended Next Features + Approach

## Recommendation 1: Conversation Summary Card
**Why**
- High user impact for long chat threads.
- Uses existing context pipeline effectively.

**Approach**
- Add `SummarizeConversationUseCase` in domain.
- Add summary repository contract + data implementation (cloud + local fallback).
- Expose `SummaryUiState` from `HomeViewModel` with `StateFlow`.
- Render a Compose summary card in `HomeScreen`.

## Recommendation 2: Personalized Suggestion Ranking
**Why**
- Increases relevance and reduces copy/edit effort.
- Builds on existing copy/send/template interaction events.

**Approach**
- Track selection events in local persistence.
- Create `RankSuggestionsUseCase` and `GetPersonalizationInsightsUseCase`.
- Apply ranking in ViewModel before emitting UI state.
- Keep Composables stateless and ViewModel-driven.

## Recommendation 3: Notification Quick Actions with AI Replies
**Why**
- Enables faster replies without opening overlay/dashboard.
- Reuses current direct-reply foundation.

**Approach**
- Add generated suggestion actions directly into notification actions.
- Validate per-app policy in domain layer before exposing actions.
- Route action clicks through a testable injected event stream.
- Keep business rules outside service/UI classes.

## Recommendation 4: Replace Global Event Bus with Injected Interface
**Why**
- Better testability and cleaner architecture boundaries.
- Reduces implicit coupling between service and UI.

**Approach**
- Define `AssistantEventStream` interface.
- Provide `@Singleton` implementation via Hilt.
- Expose `SharedFlow`/`StateFlow` for events.
- Inject into services and ViewModels.

## Recommendation 5: Compose-based IME Modernization
**Why**
- Aligns with your Compose + Material 3 direction.
- Improves maintainability and shared UI behavior.

**Approach**
- Introduce IME-specific ViewModel for UI state.
- Render IME suggestion strip via `ComposeView`.
- Share the same domain/use-case pipeline as app UI.
- Keep input insertion APIs in service layer only.

## 4) Suggested Implementation Order
1. Conversation Summary Card  
2. Personalized Suggestion Ranking  
3. Notification Quick Actions  
4. Event Stream Refactor  
5. Compose IME Modernization  

This order delivers visible user value quickly, then improves maintainability and architectural cleanliness.
