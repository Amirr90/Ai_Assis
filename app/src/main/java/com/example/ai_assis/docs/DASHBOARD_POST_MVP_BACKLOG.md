# Dashboard Post-MVP Backlog

Last updated: Apr 29, 2026

## Scope

This backlog details the implementation scaffolding for dashboard section 7 features:

- Voice input shortcut from dashboard
- Conversation summarization card
- Personalization insights (preferred tones/replies)
- Reply-later reminders
- Notification quick actions with suggested replies
- Widget surface for last suggestion

## Feature Breakdown

### 1) Voice Input Shortcut
- **UI entry:** `presentation/ui/screen/HomeScreen.kt` backlog section
- **Planned owner:** `presentation/voice/` + `domain/usecase/voice/`
- **Architecture boundary:** Dashboard emits intent/action only; voice capture/transcription stays outside Composables.
- **Effort:** M
- **Dependencies:** microphone permission flow, speech recognizer abstraction

### 2) Conversation Summarization Card
- **UI entry:** dashboard backlog card placeholder
- **Planned owner:** `domain/usecase/SummarizeConversationUseCase`
- **Architecture boundary:** Summary generated in use case/repository; UI renders a card model (`summary`, `source`, `timestamp`).
- **Effort:** M
- **Dependencies:** conversation context provider, fallback summarizer strategy

### 3) Personalization Insights
- **UI entry:** dashboard backlog card placeholder
- **Planned owner:** `domain/usecase/GetPersonalizationInsightsUseCase`
- **Architecture boundary:** Event tracking in data layer; aggregation in domain layer; dashboard reads immutable UI state.
- **Effort:** L-M
- **Dependencies:** selection telemetry, tone preference history

### 4) Reply-Later Reminders
- **UI entry:** dashboard backlog card placeholder
- **Planned owner:** `domain/usecase/ScheduleReplyReminderUseCase`
- **Architecture boundary:** Scheduling handled by worker/alarm component; dashboard only configures and observes status.
- **Effort:** M
- **Dependencies:** WorkManager/AlarmManager integration, notification channel

### 5) Notification Quick Actions
- **UI entry:** dashboard backlog card placeholder
- **Planned owner:** `service/ChatNotificationService.kt` and action composer module
- **Architecture boundary:** Notification action rendering in service; reply generation and safety constraints in domain layer.
- **Effort:** M-H
- **Dependencies:** direct-reply contract, app-specific behavior testing

### 6) Widget Surface for Last Suggestion
- **UI entry:** dashboard backlog card placeholder
- **Planned owner:** `widget/` package (`AppWidgetProvider`, update use case)
- **Architecture boundary:** Widget reads from a small presentation cache model, not live service state directly.
- **Effort:** M
- **Dependencies:** widget update scheduler, minimal persisted presentation state

## Scaffolding Added in MVP

- Dashboard now includes a visible "Post-MVP Dashboard Backlog" card so feature intent is discoverable.
- Each roadmap item is listed in the UI as "Coming soon" without enabling unfinished flows.
- MVP behavior remains unchanged because no new business flow is activated.

## Suggested Delivery Order

1. Voice input shortcut
2. Conversation summarization card
3. Personalization insights
4. Reply-later reminders
5. Notification quick actions
6. Widget surface for last suggestion
