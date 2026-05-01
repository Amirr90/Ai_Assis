---
name: ai-cost-optimization
description: Reduce LLM cost and latency through prompt design, token reduction, and caching strategies. Use when implementing or reviewing AI features, prompt templates, message pipelines, or when the user mentions token usage, model cost, cache, or prompt optimization.
disable-model-invocation: true
---

# AI Cost Optimization

Apply this skill for cost-sensitive AI features in Android/Kotlin projects.

## Primary goals

- Minimize tokens sent and returned.
- Avoid duplicate model calls.
- Keep response quality acceptable for the use case.
- Reduce latency alongside cost.

## Workflow

1. Define task intent and required output shape.
2. Trim input context to only required facts.
3. Use a constrained prompt template.
4. Apply cache lookup before model call.
5. Validate output quality and token usage.
6. Iterate on prompt + cache key rules.

## Prompt design rules

- Use explicit role and one clear task statement.
- Constrain output format (`json`, bullet list, fixed count, max words).
- Provide only essential context; remove narrative and repeated instructions.
- Prefer deterministic instructions over open-ended creative wording.
- Include hard limits (example: "Return max 3 replies, each <= 10 words").
- Keep system and developer instructions stable across requests to improve cache hit patterns.

## Token reduction tactics

- Send summaries, not full history.
- Truncate old conversation by recency + relevance.
- Remove duplicate whitespace, boilerplate, and repeated labels.
- Replace verbose field names with short, clear keys when passing JSON.
- Use retrieval filtering: top-k relevant chunks only.
- Lower max output tokens for short-form tasks.
- Skip model calls for non-actionable or unsupported inputs.

## Caching strategies

Use two cache layers when possible:

1. **Semantic/request cache (app layer)**  
   Key by normalized prompt input + task type + model + settings.
2. **Response reuse cache (feature layer)**  
   Reuse previous outputs for repeated user intents in short time windows.

### Cache key design

- Normalize text (`trim`, lowercase if case-insensitive, collapse spaces).
- Include fields that change output:
  - model id
  - temperature/top-p
  - prompt version
  - locale/tone
  - task type
- Hash large keys to fixed-length identifiers.

### TTL guidance

- Fast-changing chat context: 30s to 5m.
- Semi-stable support answers: 30m to 24h.
- Static transformations/classification: 1d to 7d.

### Invalidation rules

- Bump prompt version when prompt logic changes.
- Invalidate on user profile/tone changes.
- Invalidate on model switch.
- Use stale-while-revalidate for low-risk read scenarios.

## Android implementation expectations

- Keep caching and prompt optimization in data/domain layers.
- Expose only clean UI state via ViewModel (`StateFlow`).
- Track metrics per request:
  - input tokens
  - output tokens
  - cache hit/miss
  - latency
  - estimated cost
- Add guardrails to prevent excessive retries and duplicate calls.

## Kotlin patterns (minimal)

```kotlin
data class PromptConfig(
    val version: String,
    val model: String,
    val temperature: Double
)

fun buildCacheKey(
    task: String,
    normalizedInput: String,
    config: PromptConfig
): String = "${task}|${config.version}|${config.model}|${config.temperature}|$normalizedInput"
```

```kotlin
suspend fun generateWithCache(
    key: String,
    ttlMillis: Long,
    cacheGet: suspend (String) -> String?,
    cachePut: suspend (String, String, Long) -> Unit,
    generate: suspend () -> String
): String {
    cacheGet(key)?.let { return it }
    return generate().also { cachePut(key, it, ttlMillis) }
}
```

## Review checklist

- [ ] Prompt is concise and output-constrained.
- [ ] Context is pruned to relevant facts only.
- [ ] Max output tokens set appropriately.
- [ ] Cache key includes prompt version + model params.
- [ ] TTL and invalidation match data volatility.
- [ ] Metrics capture cost, tokens, latency, and cache hit rate.

## Output expectations when this skill is used

- Prefer minimal, production-ready Kotlin changes.
- Keep business logic out of Composables.
- Provide measurable cost impact (before/after token and hit-rate assumptions).
