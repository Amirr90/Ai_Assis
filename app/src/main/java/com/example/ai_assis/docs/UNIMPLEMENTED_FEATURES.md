# AI_Assis — Unimplemented & Pending Features

> Last updated: Apr 29, 2026  
> Legend: 🔴 Broken / Blocked &nbsp; 🟡 Partially done / needs polish &nbsp; 🔵 Not started (planned) &nbsp; 🧹 Dead code to remove

---

## 1. Critical — Broken / Non-functional

### ✅ Cloud backend URL issue fixed
**File:** `app/build.gradle.kts`  
`GEMINI_BACKEND_BASE_URL` defaults to `"https://example.com"` unless overridden in `local.properties`.  
Previously, the `GetCloudSuggestionsUseCase` → `SuggestionApiService` path failed on fresh installs.  
**Done:** `SmartSuggestionRepositoryImpl.getCloudSuggestions()` is now wired to `OpenAiApiService` and returns mapped cloud suggestions successfully.

---

### ✅ Hybrid suggestion pipeline connected
**Files:** `presentation/suggestions/SuggestionsScreen.kt`, `SuggestionsViewModel.kt`, `SuggestionsEvent.kt`, `SuggestionsEffect.kt`, `SuggestionsUiState.kt`  
Previously, `SuggestionsScreen` was not in `NavHost`, and `SuggestionsEvent.NewMessageArrived` was never triggered.  
**Done:** `Screen.Suggestions` route is added in `MainActivity`, `HomeScreen` includes navigation entry, and `SuggestionsViewModel` now collects `NotificationEventBus.events`.

---

### ✅ `OnDeviceSuggestionGenerator` expanded
**File:** `data/local/OnDeviceSuggestionGenerator.kt`  
Previously, generator logic was a small stub with fixed confidence.  
**Done:** Added broader English + Hinglish pattern handling (greeting, late, food, location, meeting, gratitude, busy, question fallback) and confidence tiers (`0.75`, `0.65`, `0.50`).

---

### ✅ Tone mismatch fixed (`SuggestionTone` / `ReplyTone`)
**Files:** `domain/model/SuggestionTone.kt`, `domain/model/ReplyTone.kt`, `data/local/TonePreferencesDataStore.kt`  
Previously, tone mapping was lossy because `FRIENDLY` mapped to `CASUAL`.  
**Done:** Removed `FRIENDLY` from `SuggestionTone`, updated mappings, and `SuggestionsScreen` now renders `ReplyTone` choices for consistent tone UX.

---

## 2. UI — Non-modern / Non-professional

### 🔴 Overlay bubble ignores system theme
**File:** `presentation/ui/overlay/BubbleContent.kt`  
All colors are hardcoded (`Color(0xEE1C1B1F)`, `Color(0xFF6750A4)`, etc.) with no reference to `MaterialTheme.colorScheme`. The bubble always renders dark even on devices in light mode.  
**Fix:** Use `MaterialTheme.colorScheme.surface`, `MaterialTheme.colorScheme.primary`, etc., or provide an explicit adaptive dark surface token.

---

### 🔴 Pause icon is the text string `"II"`
**File:** `presentation/ui/overlay/BubbleContent.kt` line ~143  
```kotlin
Text(text = "II", color = Color.White, ...)  // placeholder for pause icon
```  
**Fix:** Replace with `Icons.Default.Pause` (available in Material Icons Extended).

---

### 🟡 Scrim behind expanded panel has zero alpha
**File:** `service/OverlayService.kt` — `updateScrimForMode()`  
```kotlin
setBackgroundColor(0x00000000)  // fully transparent
```  
The scrim is invisible, so tapping outside the panel to dismiss it is confusing — the user sees no visual backdrop.  
**Fix:** Use `0x80000000` (50% black) for a proper modal feel.

---

### 🟡 Empty state missing inside the overlay panel
**File:** `presentation/ui/overlay/BubbleContent.kt`  
When `items` is empty and not loading/erroring, the `LazyColumn` renders nothing — no illustration, no hint text.  
**Fix:** Add an empty-state row: *"Tap a chat in WhatsApp or Instagram to generate replies"*.

---

### 🟡 Tone chips overflow on small screens
**File:** `presentation/ui/screen/HomeScreen.kt` — `ToneSelectorCard`  
Four `Button`s in a plain `Row` with no `horizontalScroll`. On screens < 360dp wide, buttons are clipped.  
**Fix:** Wrap in `FlowRow` (Material 3 experimental) or `HorizontalScrollableRow` or switch to `FilterChip`.

---

### 🟡 No `WindowInsets` padding on any screen
**Files:** `HomeScreen.kt`, `PermissionScreen.kt`, `AppFilterScreen.kt`  
`MainActivity` calls `enableEdgeToEdge()` but the screens do not consume insets with `safeDrawingPadding()` or `imePadding()`. On gesture-nav devices content can hide behind the system bar or navigation bar.  
**Fix:** Add `Modifier.systemBarsPadding()` / `safeDrawingPadding()` to each screen root.

---

### 🟡 `HomeScreen` — hardcoded semantic colors
**File:** `presentation/ui/screen/HomeScreen.kt`  
`Color(0xFF1B5E20)`, `Color(0xFF4CAF50)`, `Color(0xFF2E7D32)`, `Color(0xFFB00020)` — these don't adapt to dynamic color or dark mode.  
**Fix:** Use `MaterialTheme.colorScheme.error` for red states; define custom `success` and `warning` tokens inside `Color.kt` / `Theme.kt`.

---

### 🟡 Home history cards are display-only — no tap-to-copy
**File:** `presentation/ui/screen/HomeScreen.kt` — `MessageSuggestionCard`  
Reply chips in history are not clickable. The overlay chips copy to clipboard on tap, but the same chips in the Home history do nothing.  
**Fix:** Add `clickable { copyToClipboard(reply) }` with haptic feedback on each reply chip in `MessageSuggestionCard`.

---

### 🟡 `AppFilterScreen` — no feedback while DataStore is loading
**File:** `presentation/ui/screen/AppFilterScreen.kt`  
`uiState.apps` is empty until the first DataStore emission. The screen shows a blank list briefly.  
**Fix:** Add a `CircularProgressIndicator` while `apps.isEmpty()` before first emission, or use a loading flag in `AppFilterUiState`.

---

### 🟡 `ReplyImeService` uses Views — no Material 3
**File:** `service/ReplyImeService.kt`  
Entire IME is built with raw Android `TextView` / `LinearLayout` with hardcoded hex colors. Does not match the app's Material 3 design language.  
**Fix:** Either adopt a Compose-based IME approach (using `ComposeView` as the input view) or adopt Material You colour tokens via `MaterialComponents` XML theme.

---

### 🟡 No in-app prompt to enable the IME keyboard
**No file wires this.**  
Users have no idea they can enable the `ReplyImeService`. There is no button on `HomeScreen` that opens the IME settings screen.  
**Fix:** Add an "Enable AI Keyboard (Beta)" button in `HomeScreen` that launches `Settings.ACTION_INPUT_METHOD_SETTINGS`.

---

### 🔵 No app icon / splash screen branding
**Files:** `res/mipmap-*/`, `res/values/themes.xml`  
The launcher icon is the default Android mipmap launcher. No splash screen, no brand identity.  
**Fix:** Create a proper branded icon (foreground + background layers), add a `SplashScreen` API entry in `themes.xml`.

---

### 🔵 No onboarding illustrations
**File:** `PermissionScreen.kt`  
The permission screen has text only. Modern apps use a small illustration or icon per permission step.  
**Fix:** Add vector drawables or a simple animated `LottieAnimation` for each permission.

---

## 3. Architecture & Code Quality

### 🧹 Dead code: `SuggestionsScreen` + related files
**5 files:** `SuggestionsScreen.kt`, `SuggestionsViewModel.kt`, `SuggestionsEvent.kt`, `SuggestionsEffect.kt`, `SuggestionsUiState.kt`  
These are never navigated to. Either wire them properly or delete them.

---

### 🔴 `NotificationEventBus` is a global singleton — untestable
**File:** `service/NotificationEventBus.kt`  
`object NotificationEventBus` is shared by `OverlayService`, `ChatNotificationService`, `HomeViewModel`, `ReplyImeService`. It cannot be mocked, replaced, or scoped. Any test that touches it shares state.  
**Fix:** Convert to a class injected via Hilt `@Singleton` so it can be replaced in tests.

---

### 🟡 Duplicate notification debounce missing in the live pipeline
**File:** `service/ChatNotificationService.kt`  
The same notification from WhatsApp can fire `onNotificationPosted` multiple times for the same message (e.g. grouped notifications update). `SuggestionsViewModel` has a `duplicateDebounceMs = 2500L` guard but the live `OverlayService` pipeline does not.  
**Fix:** Track last emitted `(sender, message, appSource)` with a 2.5s debounce in `ChatNotificationService`.

---

### 🟡 `DirectReplyRegistry` — non-deterministic eviction
**File:** `service/DirectReplyRegistry.kt`  
`actions.keys.toList().take(...)` on a `ConcurrentHashMap` has no guaranteed order. The oldest entry is not necessarily what gets evicted.  
**Fix:** Use a `LinkedHashMap` wrapped in a `synchronized` block to preserve insertion order.

---

### 🟡 No rate-limiting on `NotificationEventBus.tryEmit`
If a high-volume app posts many notifications quickly (e.g. a group chat), AI calls are fired for every message with no debounce or queue depth limit beyond `extraBufferCapacity = 16`.  
**Fix:** Add a debounce or conflate with a minimum interval of ~1s between AI calls per `(appSource, sender)` pair.

---

### 🟡 `strings.xml` is empty — all UI text is hardcoded in Kotlin
All button labels, titles, and descriptions are string literals in Composable files. This blocks localization and makes A/B copy changes harder.  
**Fix:** Move all user-facing strings to `res/values/strings.xml`.

---

### 🔵 No error retry mechanism in the overlay
When `errorMessage != null` in the overlay panel, there is a red text box but no "Retry" button to re-trigger the AI call for the last message.  
**Fix:** Store the last `ChatMessage` in `NotificationEventBus` and expose a `retryLastMessage()` function; add a retry button in `ExpandedChatPanel`.

---

### 🔵 No tests for hybrid pipeline, notification parsing, or overlay logic
**Files:** `app/src/test/`  
Only `GetAiRepliesUseCaseTest` exists. The hybrid use case, circuit breaker, conversation cache, notification text extraction, and overlay rendering have zero test coverage.

---

## 4. Privacy & Play Store Compliance

### 🟡 In-memory message cache contradicts "never stored" claim
**File:** `data/local/ConversationCacheDataSource.kt`  
Recent messages are cached in memory for conversation context. The `PermissionScreen` copy says *"Your messages are NEVER stored"*. The cache is not persistent, but the claim should be nuanced: *"never stored to disk"*.  
**Fix:** Update privacy copy to say *"never stored to disk or shared"*.

---

### 🔵 No Privacy Policy URL
Play Store requires a privacy policy for apps using `NotificationListenerService` and `SYSTEM_ALERT_WINDOW`. The app has no in-app link to a privacy policy.  
**Fix:** Add a "Privacy Policy" `TextButton` on `PermissionScreen` and `HomeScreen` that opens a URL.

---

### 🔵 `data_extraction_rules.xml` has template TODO
**File:** `res/xml/data_extraction_rules.xml`  
Generated template with unmodified `TODO` comment — backup scope is not tailored to the app's actual data.  
**Fix:** Configure to exclude `DataStore` files containing message cache from cloud backup.

---

## 5. V2 / Roadmap Features (Not Started)

| Feature | Notes |
|---|---|
| 🔵 Voice input | Speak instead of type; convert to text, then get AI reply |
| 🔵 Chat summarization | TL;DR for long conversations shown in the bubble |
| 🔵 Personalization / tone learning | Learn which suggestions the user picks over time |
| 🔵 Smart scheduling / reply-later reminders | Alert when a message is ignored for N minutes |
| 🔵 Auto-send option | Where app policy allows (e.g. via Accessibility Service) |
| 🔵 Widget | Home screen widget showing last suggestion |
| 🔵 Notification action for quick reply | Show suggestion chips directly in the system notification shade |

---

## Summary Table

| Priority | Count | Category |
|---|---|---|
| ✅ Critical / Broken (resolved) | 4 | Cloud path, hybrid wiring, on-device generator, tone mismatch |
| 🟡 Needs polish | 13 | UI, architecture, debounce, strings, retry |
| 🧹 Dead code | 5 files | Suggestions presentation layer |
| 🔵 Not started | 10 | V2 features + privacy/compliance items |
