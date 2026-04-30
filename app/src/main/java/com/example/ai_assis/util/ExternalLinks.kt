package com.example.ai_assis.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.ai_assis.R

const val PRIVACY_POLICY_URL = "https://example.com/privacy-policy"
const val DATA_USE_DISCLOSURE_URL = "https://example.com/data-use-disclosure"

fun openExternalUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.dashboard_no_app_for_link), Toast.LENGTH_SHORT).show()
    }
}
