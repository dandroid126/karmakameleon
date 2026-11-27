package com.karmakameleon.android.ui.components

import android.net.Uri
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import com.karmakameleon.shared.ui.comment.imageLink

@Composable
fun ReplyBar(
    replyText: String,
    onReplyTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Write a reply...",
    // Drafts and quoting are only shown with showDraftControls, which the post screen's composer sets.
    onSaveDraft: () -> Unit = {},
    onLoadDraft: () -> Unit = {},
    onQuote: () -> Unit = {},
    hasDraft: Boolean = false,
    showDraftControls: Boolean = false,
    images: ReplyImageActions? = null,
) {
    val field = remember { ReplyFieldRef() }
    val formatting = remember { ReplyFormattingState(field) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier =
            modifier
                .fillMaxWidth()
                .imePadding(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
        ) {
            if (formatting.barShown) ReplyFormattingBar(formatting, field)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
                if (images?.showsButton == true) {
                    // The image goes in at the cursor, so it's added in the text field rather than the preview.
                    AddImageButton(images, onInsert = field::insert, onOpen = { formatting.preview(false) })
                }
                MarkdownHelperButton(checked = formatting.barShown, onCheckedChange = formatting::showBar)
                ReplyBarTextActions(
                    canSaveDraft = replyText.isNotBlank(),
                    hasDraft = hasDraft,
                    showDraftControls = showDraftControls,
                    onQuote = onQuote,
                    onLoadDraft = onLoadDraft,
                    onSaveDraft = onSaveDraft,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSubmit, enabled = replyText.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Default.Send, contentDescription = "Send")
                }
            }
            images?.let { ReplyImageTray(it, replyText) }
            ReplyEditor(
                previewing = formatting.previewing,
                replyText = replyText,
                onReplyTextChange = onReplyTextChange,
                placeholder = placeholder,
                field = field,
                images = images,
            )
        }
    }
    formatting.linkTarget?.let { target ->
        ReplyLinkDialog(target, field, onClose = { formatting.linkTarget = null })
    }
}

@Composable
private fun ReplyFormattingBar(
    formatting: ReplyFormattingState,
    field: ReplyFieldRef,
) {
    FormattingBar(
        previewing = formatting.previewing,
        onFormat = { markers -> field.edit { it.toggleMarkers(markers) } },
        onLink = { formatting.linkTarget = field.current() },
        onPreviewChange = formatting::preview,
    )
}

// The formatting bar's state. The bar is hidden each time a reply is opened, since the bar leaves the screen when it
// closes.
private class ReplyFormattingState(
    private val field: ReplyFieldRef,
) {
    var barShown by mutableStateOf(false)
        private set
    var previewing by mutableStateOf(false)
        private set

    // The text and selection the link dialog replaces, while it's open.
    var linkTarget by mutableStateOf<TextWithSelection?>(null)

    fun preview(on: Boolean) {
        if (on && !previewing) field.leaveForPreview()
        previewing = on
    }

    // Hiding the bar hides its Preview too.
    fun showBar(show: Boolean) {
        barShown = show
        if (!show) preview(false)
    }
}

// The text field, or in its place the preview.
@Composable
private fun ReplyEditor(
    previewing: Boolean,
    replyText: String,
    onReplyTextChange: (String) -> Unit,
    placeholder: String,
    field: ReplyFieldRef,
    images: ReplyImageActions?,
) {
    val currentImages by rememberUpdatedState(images)
    if (previewing) {
        ReplyPreview(
            replyText = replyText,
            images = images?.images.orEmpty(),
            maxHeight = replyFieldMaxHeight(),
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        SpellCheckedTextField(
            value = replyText,
            onValueChange = onReplyTextChange,
            placeholder = placeholder,
            fieldRef = field,
            acceptsImages = images != null,
            // Where replies can't have images, a keyboard GIF that comes with a link goes in as the link.
            onReceiveImage = { uri, mimeType, linkUrl ->
                currentImages?.attachImage(uri.toString(), mimeType, linkUrl) ?: linkUrl?.let { imageLink(mimeType, it) }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = replyFieldMaxHeight()),
        )
    }
}

// Asks for a link and puts it in place of the target's selection, or puts the focus back in the field.
@Composable
private fun ReplyLinkDialog(
    target: TextWithSelection,
    field: ReplyFieldRef,
    onClose: () -> Unit,
) {
    LinkDialog(
        selectedText = target.selectedText,
        onInsert = { displayText, url ->
            onClose()
            field.edit { target.replaceSelection(linkMarkdown(displayText, url)) }
        },
        onDismiss = {
            onClose()
            field.focus()
        },
    )
}

// The reply's text field, for changing its text from outside it.
private class ReplyFieldRef {
    var editText: ContentReceivingEditText? = null

    // Where the cursor was when the field left for the preview, to put it back when it returns.
    private var selectionBeforePreview: Pair<Int, Int>? = null

    fun insert(text: String) {
        editText?.apply {
            insertAtCursor(text)
            requestFocus()
        }
    }

    // Without a cursor, as before the field is first tapped, it's at the end.
    fun current(): TextWithSelection? =
        editText?.let {
            val text = it.text.toString()
            TextWithSelection(
                text,
                it.selectionStart.takeIf { s -> s >= 0 } ?: text.length,
                it.selectionEnd.takeIf { e -> e >= 0 } ?: text.length,
            )
        }

    // Changes only the part of the text that differs, so the spell checker's marks elsewhere stay, and keeps the focus
    // and the keyboard.
    fun edit(change: (TextWithSelection) -> TextWithSelection) {
        val editText = editText ?: return
        val old = editText.text.toString()
        val result = change(current() ?: return)
        val new = result.text
        val prefix = old.commonPrefixWith(new).length
        val suffix = old.substring(prefix).commonSuffixWith(new.substring(prefix)).length
        editText.text.replace(prefix, old.length - suffix, new, prefix, new.length - suffix)
        editText.setSelection(result.selectionStart, result.selectionEnd)
        focus()
    }

    fun focus() {
        editText?.apply {
            requestFocus()
            context.getSystemService(InputMethodManager::class.java)?.showSoftInput(this, 0)
        }
    }

    // The field leaves the screen for the preview, and the keyboard goes with it.
    fun leaveForPreview() {
        editText?.apply {
            selectionBeforePreview = selectionStart to selectionEnd
            context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(windowToken, 0)
        }
    }

    // A new field, made after the preview, gets the cursor back where it was, and the focus.
    fun attach(editText: ContentReceivingEditText) {
        this.editText = editText
        val selection = selectionBeforePreview ?: return
        selectionBeforePreview = null
        val length = editText.text.length
        editText.setSelection(selection.first.coerceIn(0, length), selection.second.coerceIn(0, length))
        editText.post { focus() }
    }
}

// Half the space left above the keyboard, so the whole bar stays visible.
@Composable
private fun replyFieldMaxHeight(): Dp {
    val density = LocalDensity.current
    val imeHeight = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val windowHeight =
        with(density) {
            LocalWindowInfo.current.containerSize.height
                .toDp()
        }
    return (windowHeight - imeHeight) / 2
}

// Quote, and with draft controls, Load Draft (when there's one) and Save Draft (when there's text).
@Composable
private fun ReplyBarTextActions(
    canSaveDraft: Boolean,
    hasDraft: Boolean,
    showDraftControls: Boolean,
    onQuote: () -> Unit,
    onLoadDraft: () -> Unit,
    onSaveDraft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onQuote) {
            Text("Quote", style = MaterialTheme.typography.labelSmall)
        }
        if (showDraftControls) {
            if (hasDraft) {
                TextButton(onClick = onLoadDraft) {
                    Text("Load Draft", style = MaterialTheme.typography.labelSmall)
                }
            }
            TextButton(onClick = onSaveDraft, enabled = canSaveDraft) {
                Text("Save Draft", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// Compose text fields don't show the system spell checker's underlines, so this wraps a
// platform EditText styled to look like an OutlinedTextField.
@Composable
private fun SpellCheckedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    fieldRef: ReplyFieldRef,
    acceptsImages: Boolean,
    onReceiveImage: (location: Uri, mimeType: String?, linkUrl: String?) -> String?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val currentOnReceiveImage by rememberUpdatedState(onReceiveImage)
    val fontSize = MaterialTheme.typography.bodyLarge.fontSize
    val density = LocalDensity.current
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    var isFocused by remember { mutableStateOf(false) }

    AndroidView(
        factory = { context ->
            ContentReceivingEditText(context).apply {
                receiveImages(keepAll = acceptsImages) { uri, mimeType, linkUrl -> currentOnReceiveImage(uri, mimeType, linkUrl) }
                background = null
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
                gravity = Gravity.TOP or Gravity.START
                isVerticalScrollBarEnabled = true
                with(density) {
                    val padding = 16.dp.roundToPx()
                    setPadding(padding, padding, padding, padding)
                    minimumHeight = OutlinedTextFieldDefaults.MinHeight.roundToPx()
                }
                setText(value)
                fieldRef.attach(this)
                doAfterTextChanged { currentOnValueChange(it?.toString().orEmpty()) }
                setOnFocusChangeListener { _, hasFocus -> isFocused = hasFocus }
            }
        },
        onRelease = { editText -> if (fieldRef.editText === editText) fieldRef.editText = null },
        update = { editText ->
            // Text changed outside the field, e.g. by Quote or Load Draft.
            if (editText.text.toString() != value) {
                val selection = editText.selectionEnd.coerceIn(0, value.length)
                editText.setText(value)
                editText.setSelection(selection)
            }
            editText.hint = placeholder
            editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.value)
            editText.setTextColor(colors.onSurface.toArgb())
            editText.setHintTextColor(colors.onSurfaceVariant.toArgb())
            editText.highlightColor = colors.primary.copy(alpha = 0.4f).toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val primary = colors.primary.toArgb()
                editText.textCursorDrawable?.setTint(primary)
                editText.textSelectHandle?.setTint(primary)
                editText.textSelectHandleLeft?.setTint(primary)
                editText.textSelectHandleRight?.setTint(primary)
            }
        },
        modifier =
            modifier.border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) colors.primary else colors.outline,
                shape = OutlinedTextFieldDefaults.shape,
            ),
    )
}
