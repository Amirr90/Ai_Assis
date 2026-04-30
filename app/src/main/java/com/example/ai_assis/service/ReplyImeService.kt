package com.example.ai_assis.service

import android.annotation.SuppressLint
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * V2: AI Reply IME — a lightweight keyboard companion that shows AI-generated
 * reply chips in a suggestion strip. The user selects this as their keyboard
 * in Settings → Language & input → Virtual keyboard → Manage keyboards.
 *
 * The strip renders the latest suggestions from NotificationEventBus and inserts
 * the chosen reply text into the active text field via commitText().
 */
class ReplyImeService : InputMethodService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var suggestionStrip: LinearLayout
    private lateinit var progressBar: ProgressBar

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateInputView(): View {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121316"))
            elevation = 8.dpToPx().toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20.dpToPx(), 10.dpToPx(), 20.dpToPx(), 6.dpToPx())
        }

        val titleText = TextView(this).apply {
            text = "AI Smart Replies"
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#E7D9FF"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val switchButton = TextView(this).apply {
            text = "Switch keyboard"
            textSize = 11f
            setTextColor(Color.parseColor("#C9C4D0"))
            setPadding(10.dpToPx(), 6.dpToPx(), 10.dpToPx(), 6.dpToPx())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#2B2C31"))
                cornerRadius = 12.dpToPx().toFloat()
            }
            setOnClickListener { switchToNextInputMethod() }
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleSmall).apply {
            layoutParams = LinearLayout.LayoutParams(24.dpToPx(), 24.dpToPx()).apply {
                marginEnd = 8.dpToPx()
            }
            visibility = View.GONE
            indeterminateTintList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                android.content.res.ColorStateList.valueOf(Color.parseColor("#E7D9FF"))
            } else null
        }

        headerRow.addView(titleText)
        headerRow.addView(progressBar)
        headerRow.addView(switchButton)

        val scrollView = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        suggestionStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dpToPx(), 8.dpToPx(), 12.dpToPx(), 14.dpToPx())
        }

        scrollView.addView(suggestionStrip)
        rootLayout.addView(headerRow)
        rootLayout.addView(scrollView)

        observeSuggestions()
        return rootLayout
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        renderCurrentSuggestions()
    }

    private fun observeSuggestions() {
        serviceScope.launch {
            NotificationEventBus.metaState.collect { meta ->
                if (::progressBar.isInitialized) {
                    progressBar.visibility = if (meta.isLoading) View.VISIBLE else View.GONE
                }
            }
        }
        serviceScope.launch {
            NotificationEventBus.chatHistory.collect {
                if (::suggestionStrip.isInitialized) {
                    renderCurrentSuggestions()
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun renderCurrentSuggestions() {
        suggestionStrip.removeAllViews()
        val latest = NotificationEventBus.chatHistory.value.firstOrNull()
        if (latest == null) {
            val emptyHint = TextView(this).apply {
                text = "No suggestions yet — start a monitored app chat"
                textSize = 12f
                setTextColor(Color.parseColor("#B8B4C0"))
                setPadding(8.dpToPx(), 0, 8.dpToPx(), 0)
            }
            suggestionStrip.addView(emptyHint)
            return
        }

        latest.replies.forEach { reply ->
            val chip = TextView(this).apply {
                text = reply
                textSize = 13f
                setTextColor(Color.WHITE)
                background = buildChipBackground()
                setPadding(14.dpToPx(), 9.dpToPx(), 14.dpToPx(), 9.dpToPx())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = 8.dpToPx() }
                setOnClickListener { insertReply(reply) }
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> v.alpha = 0.7f
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.alpha = 1f
                    }
                    false
                }
            }
            suggestionStrip.addView(chip)
        }
    }

    private fun insertReply(text: String) {
        currentInputConnection?.also { ic ->
            ic.beginBatchEdit()
            ic.commitText(text, 1)
            ic.endBatchEdit()
        }
        NotificationEventBus.chatHistory.value.firstOrNull()
            ?.chatMessage
            ?.appSource
            ?.takeIf { it.isNotBlank() }
            ?.let { packageName ->
                OutgoingMessageSuppressor.registerOutgoing(
                    packageName = packageName,
                    text = text,
                )
            }
    }

    private fun buildChipBackground(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor("#6D5AA7"))
            cornerRadius = 18.dpToPx().toFloat()
        }
    }

    private fun switchToNextInputMethod() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToNextInputMethod(false)
        } else {
            @Suppress("DEPRECATION")
            imm.switchToNextInputMethod(window.window?.attributes?.token, false)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()
}
