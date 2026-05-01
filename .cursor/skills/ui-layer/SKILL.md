---
name: ui-layer
description: >-
  Enforces minimal Jetpack Compose UI driven by ViewModel state, no business
  logic in composables, and explicit loading/error handling. Use when building
  or refactoring screens, Composables, navigation UI, Material 3 layouts, UI
  state, or when the user mentions UI layer, presentation layer, or screen state.
disable-model-invocation: true
---

# UI Layer Skill

Apply these rules whenever implementing or reviewing Android UI (prefer Jetpack Compose and Material 3).

## UI rules

- Keep UI clean and minimal
- Use state from ViewModel only
- Avoid business logic in UI
- Handle loading and error states properly

## How to apply

- **State**: Expose a single UI state type (or small set of sealed flows) from the ViewModel; collect it in the screen Composable and pass values/events down. Use state hoisting for reusable children.
- **Logic**: UI may format for display (strings, visual mapping) and handle focus/animation. Route decisions, validation rules, network/repo calls, and domain rules stay in ViewModel or domain layer.
- **Loading / error**: Represent these in UI state (e.g. `isLoading`, `errorMessage`, or `Async`/`Result`-style fields). Render distinct UI for loading, success, and failure; offer recovery when appropriate (retry, dismiss) via callbacks the ViewModel handles.
- **Minimal UI**: Prefer clear hierarchy, consistent spacing/typography from the theme, and avoid decorative clutter unless the product spec requires it.
