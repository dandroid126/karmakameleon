package com.karmakameleon.android.ui.components

import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.widget.EditText
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged

@Composable
fun ReplyBar(
    replyText: String,
    onReplyTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    placeholder: String = "Write a reply...",
    onSaveDraft: () -> Unit = {},
    onLoadDraft: () -> Unit = {},
    onQuote: () -> Unit = {},
    hasDraft: Boolean = false,
    showDraftControls: Boolean = false
) {
    // Cap the text field at half the space left above the keyboard so the whole bar stays visible.
    val imeHeight = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }
    val maxHeight = (LocalConfiguration.current.screenHeightDp.dp - imeHeight) / 2

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
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
                        TextButton(
                            onClick = onSaveDraft,
                            enabled = replyText.isNotBlank()
                        ) {
                            Text("Save Draft", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                IconButton(
                    onClick = onSubmit,
                    enabled = replyText.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Default.Send, contentDescription = "Send")
                }
            }
            SpellCheckedTextField(
                value = replyText,
                onValueChange = onReplyTextChange,
                placeholder = placeholder,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
            )
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
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val fontSize = MaterialTheme.typography.bodyLarge.fontSize
    val density = LocalDensity.current
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    var isFocused by remember { mutableStateOf(false) }

    AndroidView(
        factory = { context ->
            EditText(context).apply {
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
                doAfterTextChanged { currentOnValueChange(it?.toString().orEmpty()) }
                setOnFocusChangeListener { _, hasFocus -> isFocused = hasFocus }
            }
        },
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
        modifier = modifier.border(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) colors.primary else colors.outline,
            shape = OutlinedTextFieldDefaults.shape
        )
    )
}
