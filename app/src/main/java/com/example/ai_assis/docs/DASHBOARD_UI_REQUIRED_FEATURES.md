# Dashboard UI Required Features

Last updated: Apr 29, 2026

## Scope

This checklist covers dashboard-related user surfaces:

- `HomeScreen` (`presentation/ui/screen/HomeScreen.kt`)
- Floating overlay (`presentation/ui/overlay/BubbleContent.kt`)
- Overlay runtime interactions (`service/OverlayService.kt`)
- App source filter (`presentation/ui/screen/AppFilterScreen.kt`)
- Suggestions screen route (`presentation/suggestions/SuggestionsScreen.kt`)

---

## 1) Core Dashboard Features (Must Have)

These are required for a functional V1 dashboard experience.

- [x] Assistant status card with clear Running/Stopped state.
- [x] Start/Stop assistant actions (overlay service controls).
- [x] Permission status card with one-tap "Fix" actions for:
  - [x] Notification access
  - [x] Overlay permission
- [x] Tone selection UI with persisted tone state.
- [x] Monitored apps entry point from dashboard.
- [x] AI suggestions entry point from dashboard (state-aware enable/disable).
- [x] Chat history section with:
  - [x] sender and source metadata
  - [x] message preview
  - [x] actionable reply chips (copy/send behavior)
- [x] Clear history action.
- [x] Empty, loading, and error states in chat surfaces.
- [x] Privacy policy entry point on dashboard.

---

## 2) Floating Panel Features (Must Have)

- [x] Bubble head mode with unread badge and loading indicator.
- [x] Expanded panel mode with:
  - [x] source tabs (`All`, `WhatsApp`, `Instagram`, `LinkedIn`)
  - [x] suggestion cards per message
  - [x] `Show more / Show less` for long messages
  - [x] reply tap-to-copy
  - [x] direct send action where reply action key exists
- [x] Panel actions:
  - [x] Pause/Resume updates
  - [x] Clear
  - [x] Collapse
- [x] Tap outside to dismiss panel (scrim behavior).
- [x] Draggable bubble with edge snap + persisted position.

---

## 3) Dashboard UX/Design Quality Requirements

- [x] Full Material 3 color/token usage (no hardcoded semantic colors in dashboard cards and overlay).
- [x] Proper visible scrim behind expanded panel.
- [x] Insets-safe layout (`safeDrawingPadding` or equivalent) on all dashboard screens.
- [x] Consistent iconography and typography hierarchy across dashboard cards.
- [x] Source icon consistency across home cards, overlay cards, and tabs.
- [x] Non-blocking feedback for errors (clear fallback messaging when cloud provider is unavailable).

---

## 4) Reliability and Behavior Requirements

- [x] Ordered cloud provider strategy for dashboard suggestions:
  - [x] primary cloud provider
  - [x] secondary cloud provider fallback
  - [x] on-device final fallback
- [x] Provider-specific cooldown/backoff for quota/rate-limit errors.
- [x] Clear fallback reason semantics shown in logs/UI metadata.
- [x] Duplicate notification/message debounce to avoid repeated suggestion cards.
- [x] Retry mechanism for last failed suggestion request from overlay/home.

---

## 5) Accessibility and Product Requirements

- All dashboard actions must have meaningful content descriptions.
- Tap targets sized for accessibility (chips/buttons/icons in overlay panel).
- User-facing text moved to `strings.xml` for localization support.
- Keyboard/IME enablement entry point clearly visible on dashboard.

---

## 6) Compliance and Trust Requirements

- Privacy language must match actual behavior (memory cache vs disk persistence).
- Dashboard must expose privacy policy and related data-use disclosure entry points.
- Error messaging should avoid exposing raw provider internals to users.

---

## 7) Post-MVP Dashboard Backlog

- Voice input shortcut from dashboard.
- Conversation summarization card.
- Personalization insights (preferred tones/replies).
- Reply-later reminders.
- Notification quick actions with suggested replies.
- Widget surface for last suggestion.

---

## Suggested Delivery Priority

1. Core dashboard + floating panel must-haves  
2. UX/design quality and reliability  
3. Accessibility/compliance hardening  
4. Post-MVP backlog
