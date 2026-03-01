package com.karmakameleon.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

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
    val maxHeight = (LocalConfiguration.current.screenHeightDp / 2).dp

    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
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
            OutlinedTextField(
                value = replyText,
                onValueChange = onReplyTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
                placeholder = { Text(placeholder) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
        }
    }
}
