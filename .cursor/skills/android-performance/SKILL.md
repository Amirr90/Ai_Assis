---
name: android-performance
description: Improve Android app runtime performance with Jetpack Compose optimization, recomposition control, and memory leak prevention. Use when profiling jank, reducing unnecessary recompositions, fixing sluggish screens, tuning list performance, or investigating memory growth/leaks.
disable-model-invocation: true
---

# Android Performance Skill

Apply this skill when implementing or reviewing performance improvements in Kotlin Android apps that use Jetpack Compose, MVVM, and coroutines.

## Quick workflow

1. Identify the bottleneck first (recomposition, heavy main-thread work, allocations, or leak).
2. Apply the smallest change that targets the root cause.
3. Re-measure after each change to confirm real improvement.

## Compose optimization defaults

- Keep composables stateless where possible; hoist mutable state to the ViewModel or parent.
- Use stable immutable UI models to reduce unnecessary recompositions.
- Avoid expensive work inside composables; move it to ViewModel/use cases and cache results.
- Prefer lazy containers (`LazyColumn`, `LazyVerticalGrid`) for large lists.
- Provide stable keys in lazy lists using `key = { item.id }`.
- Use `remember` for pure UI memoization and `derivedStateOf` for derived values that would otherwise recalculate often.

## Recomposition control checklist

- Read only the smallest state required in each composable.
- Split large composables into smaller children so state reads are localized.
- Avoid creating new lambdas/objects on every recomposition when they can be remembered.
- Use `collectAsStateWithLifecycle()` for `StateFlow` and expose UI-ready state from ViewModel.
- Convert rapidly changing sources to coarse-grained UI state when pixel-perfect updates are not needed.
- For side effects, use the correct APIs: `LaunchedEffect`, `DisposableEffect`, `rememberUpdatedState`, `SideEffect`.

## Memory leak prevention checklist

- Never store `Activity`, `Fragment`, `View`, or `Context` in singletons/repositories unless `applicationContext` is required and safe.
- Cancel long-running work with lifecycle-aware scopes (`viewModelScope`, `repeatOnLifecycle`).
- Clean up listeners, callbacks, and observers in `DisposableEffect` or lifecycle callbacks.
- Avoid static references to UI objects and adapters.
- In Compose, ensure effects that register resources also unregister them in `onDispose`.
- Watch for bitmap/image cache growth and bound cache sizes explicitly.

## Common fixes by symptom

- **Janky scroll**: remove heavy work from item composables, add stable keys, reduce overdraw, and debounce expensive formatting.
- **High recomposition count**: narrow state reads, make parameter types stable, and split large composables.
- **Memory keeps growing after navigation**: check retained references, uncancelled coroutines, and unremoved listeners.

## Guardrails

- Prefer ViewModel/domain changes over UI workarounds.
- Do not trade correctness for speed; preserve behavior and tests.
- If no profiler evidence exists, add lightweight logging/metrics first, then optimize.

## Output expectations

When applying this skill, provide:

1. Root-cause hypothesis linked to observed symptoms.
2. Minimal code changes grouped by layer (UI/ViewModel/data).
3. Verification steps (what to measure before/after).
4. Any residual risks or follow-up profiling tasks.
