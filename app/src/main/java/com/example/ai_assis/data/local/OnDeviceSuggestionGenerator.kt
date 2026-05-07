package com.example.ai_assis.data.local

import com.example.ai_assis.domain.model.ConversationContext
import com.example.ai_assis.domain.model.Suggestion
import com.example.ai_assis.domain.model.SuggestionSource
import com.example.ai_assis.domain.usecase.BuildCandidateBehaviorsUseCase
import javax.inject.Inject

class OnDeviceSuggestionGenerator @Inject constructor() {
    private val behaviorBuilder = BuildCandidateBehaviorsUseCase()

    fun generate(context: ConversationContext): List<Suggestion> {
        val text = context.latestMessage.lowercase()
        val isHindiScript = context.latestMessage.any { it.code in 0x0900..0x097F }
        val isHinglish = !isHindiScript && text.hasAny(
            "kya",
            "kaise",
            "kaha",
            "kab",
            "milna",
            "aoge",
            "bhai",
            "haan",
            "nahi",
            "thik",
            "acha",
            "baad mein",
        )
        val replies = when {
            text.hasAny("hello", "hi", "hey", "hii", "kya haal", "namaste") -> {
                when {
                    isHindiScript -> listOf("नमस्ते! कैसे हो?", "हाय, सब ठीक?", "हेलो! क्या हाल है?")
                    isHinglish -> listOf("Hey! Kya haal hai?", "Hi! Sab thik?", "Hello ji, kaise ho?")
                    else -> listOf("Hey! How are you?", "Hi there!", "Hello!")
                }
            }

            text.hasAny("late", "der", "rukjao", "on my way", "traffic") -> {
                when {
                    isHindiScript -> listOf("मैं रास्ते में हूँ।", "थोड़ी देर होगी, सॉरी।", "10 मिनट में पहुंचता हूँ।")
                    isHinglish -> listOf("On the way hoon.", "Thodi der hogi, sorry.", "10 min mein pahuchta hoon.")
                    else -> listOf("On my way!", "Running a bit late, sorry.", "Be there in 10 minutes.")
                }
            }

            text.hasAny("khana", "lunch", "dinner", "food", "meal") -> {
                when {
                    isHindiScript -> listOf("हाँ, मुझे भी भूख लगी है।", "चलो, साथ में खाते हैं।", "क्या प्लान है?")
                    isHinglish -> listOf("Haan, mujhe bhi bhook lagi hai.", "Sure, chalo khate hain.", "Kya plan hai?")
                    else -> listOf("Yes, hungry too!", "Sure, let's eat.", "What's the plan?")
                }
            }

            text.hasAny("where", "kaha", "location", "kidhar") -> {
                when {
                    isHindiScript -> listOf("लोकेशन भेज रहा हूँ।", "मैं पास ही हूँ।", "बस पहुँचने वाला हूँ।")
                    isHinglish -> listOf("location bhej raha hoon.", "main nearby hoon.", "bas pahuchne wala hoon.")
                    else -> listOf("Sharing location.", "I am nearby.", "Almost there.")
                }
            }

            text.hasAny("meet", "milna", "kab aoge", "aoge", "meeting") -> {
                when {
                    isHindiScript -> listOf("मैं आ जाऊँगा।", "5 बजे मिलें?", "थोड़ा लेट हो सकता हूँ।")
                    isHinglish -> listOf("main aa jaunga.", "5 baje milte hain?", "thoda late ho sakta hoon.")
                    else -> listOf("I'll be there.", "Let's meet at 5?", "Might be a little late.")
                }
            }

            text.hasAny("thanks", "thank you", "shukriya", "thx") -> {
                when {
                    isHindiScript -> listOf("कोई बात नहीं!", "जब भी चाहिए, बताओ।", "मदद करके खुशी हुई।")
                    isHinglish -> listOf("koi baat nahi!", "anytime yaar.", "done bhai.")
                    else -> listOf("No worries!", "Anytime!", "Got you.")
                }
            }

            text.hasAny("busy", "baad mein", "later", "call later", "abhi nahi") -> {
                when {
                    isHindiScript -> listOf("ठीक है, बाद में बात करते हैं।", "कोई बात नहीं।", "फ्री हो जाओ तो ping करना।")
                    isHinglish -> listOf("Theek hai, baad mein baat karte hain.", "No worries.", "Free ho jao to ping karna.")
                    else -> listOf("Okay, talk later.", "No worries!", "Ping me when free.")
                }
            }

            text.contains("?") -> {
                when {
                    isHindiScript -> listOf("हाँ, बोलो?", "हाँ, करते हैं।", "ठीक है, चलता हूँ।")
                    isHinglish -> listOf("haan bol?", "haan, karte hain.", "theek hai, chalo.")
                    else -> listOf("Yeah?", "Sure, let's do it.", "Okay, sounds good.")
                }
            }

            else -> {
                when {
                    isHindiScript -> listOf("ठीक है।", "समझ गया।", "कर देता हूँ।")
                    isHinglish -> listOf("theek hai.", "samajh gaya.", "done.")
                    else -> listOf("Got it.", "Cool.", "Done.")
                }
            }
        }

        val confidence = when {
            text.hasAny(
                "hello",
                "hi",
                "hey",
                "hii",
                "kya haal",
                "namaste",
                "late",
                "der",
                "rukjao",
                "on my way",
                "traffic",
                "khana",
                "lunch",
                "dinner",
                "food",
                "meal",
                "where",
                "kaha",
                "location",
                "kidhar",
                "meet",
                "milna",
                "kab aoge",
                "aoge",
                "meeting",
                "thanks",
                "thank you",
                "shukriya",
                "thx",
                "busy",
                "baad mein",
                "later",
                "call later",
                "abhi nahi",
            ) -> 0.75
            text.contains("?") -> 0.65
            else -> 0.50
        }

        val styledReplies = applyStyleMemory(
            replies = replies,
            styleHint = context.styleHint,
            isHindiScript = isHindiScript,
            isHinglish = isHinglish,
        )

        val behaviors = behaviorBuilder(context)
        val objectivePrefix = when (context.replyObjective) {
            com.example.ai_assis.domain.model.ReplyObjective.CONTINUE_BANTER -> "haha "
            com.example.ai_assis.domain.model.ReplyObjective.PROVIDE_REASSURANCE -> "it's okay, "
            com.example.ai_assis.domain.model.ReplyObjective.CONFIRM_PLAN -> "done, "
            com.example.ai_assis.domain.model.ReplyObjective.RESOLVE_TENSION -> "sorry, "
            com.example.ai_assis.domain.model.ReplyObjective.KEEP_IT_BRIEF -> ""
        }
        val augmentedReplies = styledReplies.flatMap { base ->
            behaviors.take(2).map { behavior ->
                when (behavior) {
                    com.example.ai_assis.domain.model.CandidateBehavior.TEASING -> "$objectivePrefix$base 😄"
                    com.example.ai_assis.domain.model.CandidateBehavior.EMOTIONALLY_WARM -> "$objectivePrefix$base"
                    com.example.ai_assis.domain.model.CandidateBehavior.PLAYFUL_ESCALATION -> "$objectivePrefix$base 😉"
                    com.example.ai_assis.domain.model.CandidateBehavior.DRY_FUNNY -> base.removeSuffix(".")
                    com.example.ai_assis.domain.model.CandidateBehavior.SUBTLE_FLIRT -> "$objectivePrefix$base 🙂"
                    com.example.ai_assis.domain.model.CandidateBehavior.NEUTRAL_SAFE -> "$objectivePrefix$base"
                }.trim()
            }
        }.distinct()
        val cap = context.replyLength.maxChars.coerceIn(40, 400)
        return augmentedReplies.map {
            Suggestion(
                text = it.take(cap),
                confidence = confidence,
                source = SuggestionSource.ON_DEVICE,
            )
        }.take(6)
    }
}

private fun String.hasAny(vararg keywords: String): Boolean {
    return keywords.any { keyword -> this.contains(keyword) }
}

private fun applyStyleMemory(
    replies: List<String>,
    styleHint: String?,
    isHindiScript: Boolean,
    isHinglish: Boolean,
): List<String> {
    if (styleHint.isNullOrBlank()) return replies
    return when (styleHint) {
        "casual_slang" -> replies

        "professional" -> {
            replies.map { reply ->
                when {
                    isHindiScript -> reply.replace("ठीक है", "जी, ठीक है").replace("मैं", "मैं").trim()
                    isHinglish -> "Ji, ${reply.removePrefix("ji, ").removePrefix("Ji, ").trim()}"
                    else -> "Sure, ${reply.removePrefix("sure, ").removePrefix("Sure, ").trim()}"
                }
            }
        }

        "short" -> {
            replies.map { reply ->
                reply.split(Regex("[.!?]")).firstOrNull()?.trim().orEmpty().ifBlank { reply }.take(45)
            }
        }

        else -> replies
    }
}
