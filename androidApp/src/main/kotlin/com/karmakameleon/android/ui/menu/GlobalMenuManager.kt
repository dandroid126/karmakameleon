package com.karmakameleon.android.ui.menu

import androidx.compose.ui.graphics.vector.ImageVector

data class OverflowMenuItem(
    val title: String,
    val icon: ImageVector? = null,
    val onClick: () -> Unit
)

class GlobalMenuManager {
    private val screenItems = mutableListOf<OverflowMenuItem>()

    var onNavigateToProfile: (String) -> Unit = {}
    var onNavigateToOwnProfile: () -> Unit = {}
    var onNavigateToInbox: () -> Unit = {}
    var onNavigateToSettings: () -> Unit = {}

    fun setScreenItems(items: List<OverflowMenuItem>) {
        screenItems.clear()
        screenItems.addAll(items)
    }

    fun clearScreenItems() {
        screenItems.clear()
    }

    fun getScreenItems(): List<OverflowMenuItem> = screenItems.toList()
}
