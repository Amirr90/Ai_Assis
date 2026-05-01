# 🧠 Skill: AI Chat Assistant (Android)

## 🎯 Project Context

This is an Android app that provides AI-powered smart reply suggestions for messages from apps like WhatsApp and Instagram using NotificationListenerService.

The app:

* Reads notifications (NOT chats directly)
* Generates short replies using LLM
* Shows suggestions in a floating UI
* Allows copy/edit before sending

---

## ⚙️ Core Constraints (VERY IMPORTANT)

* NEVER read chats directly (only notifications)
* MUST filter self messages to avoid loops
* MUST minimize API calls (cost-sensitive system)
* MUST handle TEXT vs MEDIA messages differently
* Responses must be SHORT (max 8–10 words)

---

## 🧩 Key Features Implemented

* NotificationListenerService
* Floating overlay UI
* AI reply suggestions
* Self-message filtering system
* Media vs text classifier

---

## 🧠 Engineering Principles

* Use MVVM + Clean Architecture
* Keep logic modular (no god classes)
* Avoid unnecessary recomposition (Compose)
* Use coroutines + Flow properly
* Optimize for low latency

---

## 💰 Cost Optimization Rules

* DO NOT call AI for:

  * Media messages (reels, images, videos)
* Limit replies:

  * Max 2–3 suggestions
* Keep responses short
* Avoid sending full chat history

---

## 🧠 AI Prompt Rules

Always use:

Example:
"Generate 3 short replies (max 8 words each).
Tone: casual.
Message: {message}"

* No long paragraphs
* No explanations
* Only direct replies

---

## 🔁 Message Filtering Rules

* Ignore self-sent messages:

  * Compare with lastSentMessage
  * Check timestamp (<5 sec)
* Avoid duplicate processing:

  * Use message hash cache

---

## 🧩 UI Guidelines

* Minimal, fast UI
* Use chips for suggestions
* Each suggestion has:

  * Copy
  * Edit option
* Floating UI must be non-intrusive

---

## 🎨 Design System

* Light theme first
* Soft colors, not neon
* Rounded corners (16dp+)
* Fast animations (150–250ms)

---

## 🧪 Code Expectations

When generating code:

* Only generate required changes
* Avoid rewriting full files
* Keep functions small and reusable
* Add null safety
* Add minimal comments only

---

## 🚫 What to Avoid

* Overengineering
* Long AI responses
* Heavy UI components
* Blocking main thread
* Unnecessary API calls

---

## 🧠 Output Style (IMPORTANT)

* Prefer concise Kotlin code
* No unnecessary explanation
* Focus on production-ready implementation

---

## 🏁 Definition of Good Output

* Works correctly
* Minimal code
* Optimized for performance + cost
* Easy to integrate
