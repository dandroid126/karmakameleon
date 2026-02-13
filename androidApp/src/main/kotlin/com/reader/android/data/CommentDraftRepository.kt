package com.reader.android.data

import android.content.Context
import android.content.SharedPreferences

class CommentDraftRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveDraft(parentId: String, text: String) {
        prefs.edit().putString(parentId, text).apply()
    }

    fun loadDraft(parentId: String): String? {
        return prefs.getString(parentId, null)
    }

    fun deleteDraft(parentId: String) {
        prefs.edit().remove(parentId).apply()
    }

    companion object {
        private const val PREFS_NAME = "comment_drafts"
    }
}
