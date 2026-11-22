package com.karmakameleon.shared.domain.model

// How Reddit writes an uploaded image it hadn't finished processing into a comment's text, like
// "*Processing img 2sh3c89s183i1...*".
private val processingPlaceholder = Regex("""\*Processing \w+ ([A-Za-z0-9]+)\.\.\.\*""")

/**
 * Reddit writes a comment's text when it's posted. An image it hadn't finished processing by then stays as
 * "*Processing img <ID>...*" for good, though the comment's media gets the image once it's ready. This puts the image
 * in place of each such placeholder whose ID is in [readyMedia], which maps media IDs to image URLs.
 */
fun String.withProcessedMedia(readyMedia: Map<String, String>): String {
    if (readyMedia.isEmpty()) return this
    return processingPlaceholder.replace(this) { match -> readyMedia[match.groupValues[1]]?.let { "![img]($it)" } ?: match.value }
}
