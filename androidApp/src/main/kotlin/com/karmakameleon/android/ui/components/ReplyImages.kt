package com.karmakameleon.android.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.karmakameleon.android.ui.theme.mediaColors
import com.karmakameleon.shared.ui.comment.ReplyImage
import com.karmakameleon.shared.ui.comment.ReplyImageStatus

/**
 * The button that adds an image, with a menu of where it comes from: the photo picker, or a link. [onInsert] puts the
 * added image's token at the reply's cursor.
 */
@Composable
internal fun AddImageButton(
    images: ReplyImageActions,
    onInsert: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    val currentImages by rememberUpdatedState(images)
    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { currentImages.attachImage(it.toString(), null, null)?.let(onInsert) }
        }
    Box {
        // Where the subreddit allows no uploads, the button looks disabled but still takes a tap, to say why.
        IconButton(onClick = { if (images.canAddImages) expanded = true else images.explainDisabled() }) {
            Icon(
                Icons.Outlined.AddPhotoAlternate,
                contentDescription = if (images.canAddImages) "Add image" else "Add image, disabled in this subreddit",
                tint = if (images.canAddImages) LocalContentColor.current else IconButtonDefaults.iconButtonColors().disabledContentColor,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Choose from device") },
                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                onClick = {
                    expanded = false
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
            DropdownMenuItem(
                text = { Text("Image from link") },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                onClick = {
                    expanded = false
                    showLinkDialog = true
                },
            )
        }
    }
    if (showLinkDialog) {
        ImageLinkDialog(
            onAdd = { url ->
                showLinkDialog = false
                images.attachImageFromUrl(url)?.let(onInsert)
            },
            onDismiss = { showLinkDialog = false },
        )
    }
}

/** Asks for the web address of an image to add. */
@Composable
private fun ImageLinkDialog(
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Image from link") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = { Text("https://i.imgur.com/example.png") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(url) }, enabled = url.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/** The reply's images, each with its upload state and a button that removes it. Nothing while there are none. */
@Composable
internal fun ReplyImageTray(
    images: ReplyImageActions,
    replyText: String,
    modifier: Modifier = Modifier,
) {
    if (images.images.isEmpty()) return
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        images.images.forEachIndexed { index, image ->
            ReplyImageThumbnail(
                image = image,
                number = index + 1,
                // An image whose token the user deleted isn't sent.
                isInText = "(${image.key})" in replyText,
                onRetry = { images.retryImage(image.key) },
                onRemove = { images.removeImage(image.key) },
            )
        }
    }
}

@Composable
private fun ReplyImageThumbnail(
    image: ReplyImage,
    number: Int,
    isInText: Boolean,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = mediaColors()
    val status = image.status
    val description =
        when {
            status is ReplyImageStatus.Failed -> "Image $number: ${status.message}"
            !isInText -> "Image $number, not in the text, so it won't be sent"
            status is ReplyImageStatus.Uploaded -> "Image $number"
            else -> "Image $number, uploading"
        }
    Box(modifier = Modifier.padding(top = 6.dp, end = 6.dp)) {
        Box(
            modifier =
                Modifier
                    .size(THUMBNAIL_SIZE)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            image.image?.let {
                AsyncImage(
                    model = it.bytes,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = if (isInText) 1f else DIMMED_ALPHA,
                    modifier = Modifier.size(THUMBNAIL_SIZE),
                )
            }
            when (status) {
                ReplyImageStatus.Reading, ReplyImageStatus.Uploading -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }

                is ReplyImageStatus.Failed -> {
                    if (image.image != null) {
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier.background(colors.overlayBadgeColor, CircleShape).size(32.dp),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry upload", tint = colors.onOverlayColor)
                        }
                    } else {
                        Text("!", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleLarge)
                    }
                }

                // Shows just the image.
                is ReplyImageStatus.Uploaded -> {}
            }
        }
        RemoveImageBadge(number = number, onRemove = onRemove, modifier = Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun RemoveImageBadge(
    number: Int,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = mediaColors()
    Box(
        modifier =
            modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(colors.overlayBadgeColor)
                .clickable(onClickLabel = "Remove image $number", onClick = onRemove)
                .semantics { contentDescription = "Remove image $number" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Close, contentDescription = null, tint = colors.onOverlayColor, modifier = Modifier.size(14.dp))
    }
}

private val THUMBNAIL_SIZE = 64.dp
private const val DIMMED_ALPHA = 0.4f
