package com.reader.shared.data.repository

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

class CommentDraftRepository(private val settings: Settings) {

    fun saveDraft(parentId: String, text: String) {
        settings[PREFIX_DRAFT + parentId] = text
    }

    fun loadDraft(parentId: String): String? {
        return settings.getStringOrNull(PREFIX_DRAFT + parentId)
    }

    fun deleteDraft(parentId: String) {
        settings.remove(PREFIX_DRAFT + parentId)
    }

    companion object {
        private const val PREFIX_DRAFT = "draft_"
    }
}
