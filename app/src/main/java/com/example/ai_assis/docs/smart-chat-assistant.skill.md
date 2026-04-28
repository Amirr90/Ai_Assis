# 🧠 Skill: Smart Chat Assistant (Android + AI)

## 🎯 Objective

Build an Android application that provides AI-powered smart reply suggestions for incoming messages from apps like WhatsApp and Instagram using notification access and optional overlay UI.

---

## 🧩 Core Features (MVP)

### 1. Notification Listener

* Use `NotificationListenerService`
* Capture incoming messages from:

  * WhatsApp
  * Instagram
* Extract:

  * Sender name
  * Message content

---

### 2. AI Reply Generation

* Integrate with OpenAI API
* Generate 3 short contextual replies

#### Prompt Template:

```
Generate 3 short replies to this message.
Tone: {tone}
Message: {message}
Replies should be concise and conversational.
```

---

### 3. Floating Bubble UI (Optional MVP+)

* Use `SYSTEM_ALERT_WINDOW`
* Show overlay when new message arrives
* Expand to show reply suggestions

---

### 4. Copy to Clipboard

* On reply tap:

  * Copy text using `ClipboardManager`
  * Show toast: "Copied to clipboard"

---

### 5. Tone Selector

* Options:

  * Casual
  * Professional
  * Funny
  * Short

Store selection in:

* `SharedPreferences` or `DataStore`

---

### 6. Multi-language Support

* Detect Hinglish / Hindi input
* Return replies in same language

---

## 🏗️ Architecture

### Pattern:

* MVVM + Clean Architecture

### Layers:

* Presentation (UI, ViewModel)
* Domain (UseCases)
* Data (Repository, API)

---

## 📦 Tech Stack

* Kotlin
* Jetpack Compose (UI)
* Ktor / Retrofit (API)
* Coroutines + Flow
* Hilt (DI)
* DataStore (local storage)

---

## 🔌 Key Android Components

### Notification Listener

```kotlin
class ChatNotificationService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val message = extras.getCharSequence("android.text")?.toString()
    }
}
```

---

### Clipboard Copy

```kotlin
val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
val clip = ClipData.newPlainText("reply", text)
clipboard.setPrimaryClip(clip)
```

---

## 🤖 API Layer (Example)

### Request:

POST /generate-reply

```json
{
  "message": "Are you coming?",
  "tone": "casual"
}
```

### Response:

```json
{
  "replies": [
    "Yes, on my way!",
    "Will be there soon",
    "Running a bit late"
  ]
}
```

---

## 🔐 Permissions Required

* Notification Access
* Overlay Permission (optional)
* Internet

---

## ⚠️ Play Store Compliance

* DO NOT read chats directly from apps
* ONLY use notification data
* Show clear permission disclosure
* Avoid storing personal messages

---

## 🚀 Future Enhancements

* Keyboard integration (IME Service)
* Voice-to-reply
* Chat summarization
* Personalization (learning user tone)

---

## 🧪 Testing Strategy

* Unit tests for UseCases
* Mock API responses
* Test with real notifications

---

## 📌 Folder Structure

```
com.app.chatassistant
│
├── data
│   ├── remote
│   ├── repository
│
├── domain
│   ├── usecase
│   ├── model
│
├── presentation
│   ├── ui
│   ├── viewmodel
│
├── service
│   ├── NotificationService.kt
│   ├── OverlayService.kt
```

---

## 💡 Notes

* Keep responses under 1–2 lines
* Focus on speed (<1s response ideal)
* UX should be minimal and non-intrusive

---

## 🏁 Definition of Done (MVP)

* App reads notification messages
* AI generates replies successfully
* User can copy reply in one tap
* App runs without crashes
* Passes Play Store basic policy checks

---

## 🔥 Tagline

"Reply faster. Chat smarter."
