package com.tom7.openremote.ui.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/** OpenText's handoff entry point; it is the only thing the two apps agree on. */
const val OPENTEXT_PACKAGE = "com.tom7.opentext"
const val OPENTEXT_ACTION = "com.tom7.opentext.OPEN_REMOTE"

/**
 * Hands a session's project folder to OpenText, reusing the same server address and password this
 * connection already authenticated with. Returns false when OpenText is not installed.
 */
fun openInOpenText(
    context: Context,
    url: String,
    password: String,
    directory: String,
): Boolean {
    val intent = Intent(OPENTEXT_ACTION)
        .setPackage(OPENTEXT_PACKAGE)
        .putExtra("url", url)
        .putExtra("password", password)
        .putExtra("directory", directory)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
