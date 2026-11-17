package com.karmakameleon.shared.data.repository

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

class CommentDraftRepository(
    private val settings: Settings,
) {
    fun saveDraft(
        parentId: String,
        text: String,
    ) {
        settings[PREFIX_DRAFT + parentId] = text
    }

    fun loadDraft(parentId: String): String? = settings.getStringOrNull(PREFIX_DRAFT + parentId)

    fun deleteDraft(parentId: String) {
        settings.remove(PREFIX_DRAFT + parentId)
    }

    /** Every saved draft, by the ID of the post or comment it replies to. */
    fun allDrafts(): Map<String, String> =
        settings.keys
            .filter { it.startsWith(PREFIX_DRAFT) }
            .mapNotNull { key -> settings.getStringOrNull(key)?.let { key.removePrefix(PREFIX_DRAFT) to it } }
            .toMap()

    fun replaceAllDrafts(drafts: Map<String, String>) {
        settings.keys.filter { it.startsWith(PREFIX_DRAFT) }.forEach(settings::remove)
        drafts.forEach { (parentId, text) -> saveDraft(parentId, text) }
    }

    companion object {
        private const val PREFIX_DRAFT = "draft_"
    }
}
