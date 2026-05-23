package com.example.smartfamilygrocerylist.platform

import android.content.Context
import android.content.Intent

object ShareSheet {
    /**
     * Triggers the native Android Share Sheet to share links/tokens.
     */
    fun shareText(context: Context, text: String, title: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, title)
        // Add flag in case context is not an Activity
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }
}
