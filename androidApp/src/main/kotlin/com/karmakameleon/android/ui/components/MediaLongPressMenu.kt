package com.karmakameleon.android.ui.components

import android.content.ClipData
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class MediaMenuItem(
    val label: String,
    val onClick: () -> Unit
)

@Composable
fun imageMenuItems(url: String): List<MediaMenuItem> {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return listOf(
        MediaMenuItem("Copy Image Link") {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", url))) }
        },
        MediaMenuItem("Copy Image to Clipboard") {
            scope.launch { copyImageToClipboard(context, url) }
        },
        MediaMenuItem("Save Image") {
            scope.launch { saveImageToGallery(context, url) }
        }
    )
}

@Composable
fun videoMenuItems(videoUrl: String): List<MediaMenuItem> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = videoUrl
        .substringAfterLast("/")
        .substringBefore("?")
        .substringAfterLast(".")
        .uppercase()
        .let { if (it.isBlank() || it.length > 5) "MP4" else it }
    return listOf(
        MediaMenuItem("Save $ext Video") {
            scope.launch { saveVideoToGallery(context, videoUrl) }
        }
    )
}

@Composable
fun gifMenuItems(gifUrl: String, gifImageUrl: String? = null): List<MediaMenuItem> {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val imageUrl = gifImageUrl ?: gifUrl
    return listOf(
        MediaMenuItem("Copy GIF Link") {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", gifUrl))) }
        },
        MediaMenuItem("Copy GIF to Clipboard") {
            scope.launch { copyImageToClipboard(context, imageUrl) }
        },
        MediaMenuItem("Save GIF") {
            scope.launch { saveImageToGallery(context, imageUrl) }
        }
    )
}

@Composable
fun MediaLongPressMenu(
    items: List<MediaMenuItem>,
    expanded: Boolean,
    onDismiss: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        items.forEach { item ->
            DropdownMenuItem(
                text = { Text(item.label) },
                onClick = {
                    item.onClick()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
fun LongPressMenuBox(
    menuItems: List<MediaMenuItem>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = { if (menuItems.isNotEmpty()) showMenu = true }
            )
    ) {
        content()
        Box(modifier = Modifier.align(Alignment.BottomEnd)) {
            MediaLongPressMenu(
                items = menuItems,
                expanded = showMenu,
                onDismiss = { showMenu = false }
            )
        }
    }
}

internal suspend fun copyImageToClipboard(context: android.content.Context, url: String) {
    withContext(Dispatchers.IO) {
        try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            try {
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                connection.connect()
                val bytes = connection.inputStream.use { it.readBytes() }
                val filename = url.substringAfterLast("/").substringBefore("?").let {
                    if (it.contains(".")) it else "${it}.jpg"
                }
                val mimeType = when {
                    filename.endsWith(".gif") -> "image/gif"
                    filename.endsWith(".png") -> "image/png"
                    filename.endsWith(".webp") -> "image/webp"
                    else -> "image/jpeg"
                }
                val cacheDir = File(context.cacheDir, "shared_images")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val file = File(cacheDir, filename)
                file.writeBytes(bytes)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                withContext(Dispatchers.Main) {
                    val clipData = ClipData.newUri(context.contentResolver, "image", uri)
                    clipData.description.extras = android.os.PersistableBundle().apply {
                        putString("android.content.extra.MIME_TYPES", mimeType)
                    }
                    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboardManager.setPrimaryClip(clipData)
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to copy image", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

internal suspend fun saveImageToGallery(context: android.content.Context, url: String) {
    withContext(Dispatchers.IO) {
        try {
            val filename = url.substringAfterLast("/").substringBefore("?").let {
                if (it.contains(".")) it else "${it}.jpg"
            }
            val mimeType = when {
                filename.endsWith(".gif") -> "image/gif"
                filename.endsWith(".png") -> "image/png"
                filename.endsWith(".webp") -> "image/webp"
                else -> "image/jpeg"
            }

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, filename)
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.DATA, file.absolutePath)
                }
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            }

            if (uri != null) {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                try {
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.connect()
                    connection.inputStream.use { input ->
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            input.copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Image saved", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    connection.disconnect()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

internal suspend fun saveVideoToGallery(context: android.content.Context, url: String) {
    withContext(Dispatchers.IO) {
        try {
            val filename = url.substringAfterLast("/").substringBefore("?").let {
                if (it.contains(".")) it else "${it}.mp4"
            }
            val mimeType = when {
                filename.endsWith(".webm") -> "video/webm"
                filename.endsWith(".m3u8") -> "video/mp4"
                else -> "video/mp4"
            }

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                }
                context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, filename)
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Video.Media.DATA, file.absolutePath)
                }
                context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            }

            if (uri != null) {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                try {
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    connection.connect()
                    connection.inputStream.use { input ->
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            input.copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Video saved", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    connection.disconnect()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to save video", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
