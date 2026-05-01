---
name: ui-ux-micro-interactions
description: Design and implement polished Jetpack Compose micro-interactions with floating UI, subtle animations, and chip interactions. Use when building or refining interaction details, motion behavior, feedback states, Material 3 chips, or when the user mentions floating UI, animation polish, or chip UX.
disable-model-invocation: true
---

# UI/UX Micro-Interactions

Apply this skill when adding small, high-impact interaction polish in Android Compose apps.

## Scope

- Floating UI behavior (FABs, expanding surfaces, anchored overlays)
- Motion and transition choreography
- Chip interactions (FilterChip, SuggestionChip, AssistChip, InputChip)
- Touch feedback and response timing

## Core principles

- Keep interactions meaningful, not decorative.
- Prioritize clarity, accessibility, and performance.
- Tie visual state directly to ViewModel state.
- Keep domain logic out of Composables.
- Prefer simple motion curves and short durations.

## Implementation workflow

1. Define interaction intent and expected user feedback.
2. Model interaction state in ViewModel (`StateFlow`).
3. Hoist state and callbacks to the screen level.
4. Implement Compose animation APIs with constrained duration.
5. Add accessibility semantics and reduced-motion fallback.
6. Verify smoothness and consistency on real device/emulator.

## Floating UI guidelines

- Use `Scaffold` + `floatingActionButton` for primary FAB patterns.
- For contextual floating surfaces, anchor to stable layout bounds.
- Keep floating elements away from critical content and system bars.
- Use elevation and shape changes to communicate affordance, not color alone.
- Animate appearance/disappearance with `AnimatedVisibility` (fade + slight scale/slide).
- Preserve state across config changes when interaction mode is user-driven.

## Animation guidelines

- Prefer `animate*AsState` for simple property transitions.
- Use `updateTransition` when multiple properties must move in sync.
- Use `AnimatedContent` for state swaps and content morphs.
- Default duration range: 120ms to 300ms for micro-interactions.
- Prefer standard easing (`FastOutSlowInEasing`, `LinearOutSlowInEasing`).
- Avoid long chains and nested infinite animations on core UI paths.

### Motion constraints

- One primary motion focus per interaction.
- Keep entrance motion slightly stronger than exit motion.
- Respect interruptibility: user input should cancel or retarget ongoing animation cleanly.

## Chip interaction guidelines

- Select chip type based on intent:
  - `FilterChip`: toggles and multi-select filters.
  - `SuggestionChip`: one-tap suggested actions.
  - `AssistChip`: lightweight helper actions.
  - `InputChip`: removable selected items.
- Reflect chip selected/pressed/loading states explicitly.
- Keep selection source of truth in ViewModel.
- For multi-select chips, expose immutable selected-id sets from state.
- Add concise labels and optional leading icons only when they improve scannability.
- Ensure touch target size and spacing remain ergonomic.

## State pattern (Compose + MVVM)

```kotlin
data class ChipsUiState(
    val selectedIds: Set<String> = emptySet(),
    val isLoading: Boolean = false
)

class ChipsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ChipsUiState())
    val uiState: StateFlow<ChipsUiState> = _uiState.asStateFlow()

    fun onChipToggle(id: String) {
        _uiState.update { state ->
            state.copy(
                selectedIds = state.selectedIds.toMutableSet().apply {
                    if (!add(id)) remove(id)
                }
            )
        }
    }
}
```

## Compose snippets (minimal patterns)

```kotlin
val scale by animateFloatAsState(
    targetValue = if (selected) 1.0f else 0.96f,
    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
    label = "chipScale"
)
```

```kotlin
AnimatedVisibility(
    visible = showFloatingPanel,
    enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.96f),
    exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.98f)
) {
    FloatingActionPanel()
}
```

## Accessibility and UX quality bar

- Ensure state changes are perceivable without relying only on color.
- Keep contrast and text legibility intact during animated states.
- Provide semantic labels and selected-state announcements for chips.
- Avoid auto-motion loops that compete with readability.
- Respect system animation scale; degrade gracefully when animations are disabled.

## Performance checks

- Avoid recomposition-heavy animation triggers in large lists.
- Use stable keys for chip collections.
- Memoize expensive calculations with `remember`.
- Prefer lightweight transitions over blurred or layered overdraw-heavy effects.
- Validate jank risk on lower-end devices for interaction-heavy screens.

## Review checklist

- [ ] Interaction purpose is clear and user-facing.
- [ ] ViewModel owns interaction state; Composable is state-driven.
- [ ] Animation duration/easing is consistent and restrained.
- [ ] Floating UI does not obscure core content or controls.
- [ ] Chip behavior is type-appropriate and accessible.
- [ ] Performance remains smooth under rapid user input.
