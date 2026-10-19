package com.karmakameleon.android.ui.components

/** An entry in a media item's long-press menu. */
data class MediaMenuItem(
    val label: String,
    val onClick: () -> Unit,
)
