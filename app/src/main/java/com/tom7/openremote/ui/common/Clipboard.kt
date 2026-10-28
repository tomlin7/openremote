package com.tom7.openremote.ui.common

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch

/** Returns a function that copies text to the system clipboard from any composable. */
@Composable
fun rememberClipboardWriter(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) {
        { text ->
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("OpenRemote", text)))
            }
        }
    }
}
