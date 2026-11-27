package com.karmakameleon.android.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.karmakameleon.shared.domain.markdown.MarkdownBlock
import com.karmakameleon.shared.ui.comment.ReplyImage
import com.karmakameleon.shared.ui.comment.previewImageUrl
import com.karmakameleon.shared.ui.comment.withPreviewImages
import com.karmakameleon.shared.util.markdown.MarkdownParser
import org.koin.compose.koinInject

/** The button that shows and hides the formatting bar. It looks selected while the bar is shown. */
@Composable
internal fun MarkdownHelperButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors =
            IconButtonDefaults.iconToggleButtonColors(
                checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
    ) {
        Icon(Icons.Default.Code, contentDescription = "Formatting")
    }
}

/** The formatting buttons, which scroll sideways when they don't fit, and Preview, which stays at the end. */
@Composable
internal fun FormattingBar(
    onFormat: (MarkdownMarkers) -> Unit,
    onLink: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            FormatButton(Icons.Default.FormatItalic, "Italic") { onFormat(MarkdownMarkers.Italic) }
            FormatButton(Icons.Default.FormatBold, "Bold") { onFormat(MarkdownMarkers.Bold) }
            FormatButton(Icons.Default.FormatStrikethrough, "Strikethrough") { onFormat(MarkdownMarkers.Strikethrough) }
            FormatButton(Icons.Default.VisibilityOff, "Spoiler") { onFormat(MarkdownMarkers.Spoiler) }
            FormatButton(Icons.Default.Link, "Link", onLink)
        }
        AssistChip(
            onClick = onPreview,
            label = { Text("Preview") },
            leadingIcon = {
                Icon(
                    Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

// An icon button with its name in a tooltip on a long press.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label)
        }
    }
}

/**
 * Asks for a link's text and address. The text starts as [selectedText], and then the address has the focus. An empty
 * field is written as its label, so Insert is always enabled.
 */
@Composable
internal fun LinkDialog(
    selectedText: String,
    onInsert: (displayText: String, url: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var displayText by remember { mutableStateOf(selectedText) }
    var url by remember { mutableStateOf("") }
    val textFocus = remember { FocusRequester() }
    val urlFocus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert link") },
        text = {
            Column {
                OutlinedTextField(
                    value = displayText,
                    onValueChange = { displayText = it },
                    placeholder = { Text("Display text") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.focusRequester(textFocus),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.padding(top = 8.dp).focusRequester(urlFocus),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onInsert(displayText, url) }) {
                Text("Insert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
    LaunchedEffect(Unit) {
        // A request before the dialog's first frame is overridden: the first field gets the focus.
        withFrameNanos {}
        (if (selectedText.isEmpty()) textFocus else urlFocus).requestFocus()
    }
}

/**
 * The reply as it will look once posted, in a dialog, so it's clear it can't be edited there. The text field stays
 * where it is behind it, with its cursor. Its images that are read show where they are in the text, from the device,
 * and those still being read are left out. Links don't open, so a mistap doesn't leave the reply; spoilers still
 * reveal on a tap. The text is parsed off the main thread, only while the dialog is open.
 */
@Composable
internal fun ReplyPreviewDialog(
    replyText: String,
    images: List<ReplyImage>,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    markdownParser: MarkdownParser = koinInject(),
) {
    val localImages =
        remember(images) {
            images.mapNotNull { image -> image.image?.let { image.key to it.bytes } }.toMap()
        }
    val blocks by produceState<List<MarkdownBlock>?>(null, replyText, localImages) {
        value = markdownParser.parse(replyText.withPreviewImages(localImages.keys))
    }
    val imagesByUrl = remember(localImages) { localImages.mapKeys { (key, _) -> previewImageUrl(key) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preview") },
        text = {
            Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (replyText.isBlank()) {
                    Text(
                        "Nothing to preview",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    blocks?.let {
                        MarkdownText(
                            blocks = it,
                            style = MaterialTheme.typography.bodyLarge,
                            localImages = imagesByUrl,
                            linksOpen = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSend, enabled = replyText.isNotBlank()) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}
