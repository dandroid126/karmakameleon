package com.karmakameleon.android.ui.components

import com.karmakameleon.shared.ui.comment.ReplyImage

/** What the reply bar does with images, where replies can have them. */
data class ReplyImageActions(
    val images: List<ReplyImage>,
    // Whether to show the button that adds one, and whether it's enabled. Images the keyboard sends are handled either way.
    val showsButton: Boolean,
    val canAddImages: Boolean,
    // Says why the button is disabled, when it's tapped.
    val explainDisabled: () -> Unit,
    // Each returns the text to put at the cursor, or null when there's none.
    val attachImage: (location: String, mimeType: String?, linkUrl: String?) -> String?,
    val attachImageFromUrl: (url: String) -> String?,
    val retryImage: (key: String) -> Unit,
    val removeImage: (key: String) -> Unit,
)
