package com.karmakameleon.shared.ui.comment

import com.karmakameleon.shared.data.media.ImageFile

/** An image in the reply being written. The reply's text refers to it as `![image](<key>)`, which [imageToken] makes. */
data class ReplyImage(
    val key: String,
    val status: ReplyImageStatus,
    // Null until it's read. Kept for retrying the upload, since the permission to read it again can end.
    val image: ImageFile? = null,
)

sealed interface ReplyImageStatus {
    data object Reading : ReplyImageStatus

    data object Uploading : ReplyImageStatus

    data class Uploaded(
        val assetId: String,
    ) : ReplyImageStatus

    data class Failed(
        val message: String,
    ) : ReplyImageStatus
}

internal fun imageToken(key: String) = "![image]($key)"

// Any alt text, since the user may edit it, and only keys the app makes, so other markdown images are left alone.
private val IMAGE_TOKEN = Regex("""!\[[^\]\n]*]\((img-\d+)\)""")

/** The keys of the image tokens in this text. */
internal fun String.imageKeys(): Set<String> = IMAGE_TOKEN.findAll(this).map { it.groupValues[1] }.toSet()

/** This text with each uploaded image's token as the markdown Reddit converts to an image, `![img](<asset ID>)`. */
internal fun String.withAssetIds(assetIds: Map<String, String>): String =
    IMAGE_TOKEN.replace(this) { match -> assetIds[match.groupValues[1]]?.let { "![img]($it)" } ?: match.value }

/** This text with the token for [key] replaced, or removed. */
internal fun String.replacingImageToken(
    key: String,
    replacement: String = "",
): String = IMAGE_TOKEN.replace(this) { match -> if (match.groupValues[1] == key) replacement else match.value }

internal fun isGif(mimeType: String?) = mimeType.equals("image/gif", ignoreCase = true)
