package com.karmakameleon.android.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat

/**
 * An EditText that takes content from the keyboard, like Gboard's GIFs, through [ViewCompat.setOnReceiveContentListener].
 * From Android 12 the platform tells the keyboard what the listener takes; before that, this does.
 *
 * It's not an AppCompatEditText, which lint asks for: the app has no AppCompat theme or dependency of its own (AppCompat
 * only comes in through other libraries), and AppCompat's tinting and widget replacement don't apply to a view made in
 * code and styled by hand.
 */
@SuppressLint("AppCompatCustomView")
internal class ContentReceivingEditText(
    context: Context,
) : EditText(context) {
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return connection
        val mimeTypes = ViewCompat.getOnReceiveContentMimeTypes(this) ?: return connection
        EditorInfoCompat.setContentMimeTypes(outAttrs, mimeTypes)
        return InputConnectionCompat.createWrapper(this, connection, outAttrs)
    }

    /** Puts text where the cursor is, replacing any selection. */
    fun insertAtCursor(text: String) {
        val editable = getText()
        val start = minOf(selectionStart, selectionEnd).coerceAtLeast(0)
        val end = maxOf(selectionStart, selectionEnd).coerceAtLeast(0)
        editable.replace(start, end, text)
    }

    /**
     * Hands each image the keyboard, clipboard or a drag sends to [receive], which returns the text to put at the cursor
     * for it, or null. With [keepAll], every image counts as handled, as [receive] told the user why it put nothing in.
     * Otherwise, when nothing was put in, the content goes back to the platform, which tells the user it isn't supported.
     */
    fun receiveImages(
        keepAll: Boolean,
        receive: (location: Uri, mimeType: String?, linkUrl: String?) -> String?,
    ) {
        ViewCompat.setOnReceiveContentListener(this, arrayOf("image/*")) { _, payload ->
            // An androidx.core Pair, which can't be destructured.
            val split = payload.partition { it.uri != null }
            val uriContent = split.first
            val otherContent = split.second
            if (uriContent == null) return@setOnReceiveContentListener payload
            val clip = uriContent.clip
            val linkUrl = uriContent.linkUri?.toString()
            var inserted = false
            for (index in 0 until clip.itemCount) {
                val uri = clip.getItemAt(index).uri
                val text = receive(uri, context.contentResolver.getType(uri), linkUrl)
                if (text != null) {
                    insertAtCursor(text)
                    inserted = true
                }
            }
            if (inserted || keepAll) otherContent else payload
        }
    }
}
