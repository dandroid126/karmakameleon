package com.reader.android.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _inlineImagesEnabled = MutableStateFlow(prefs.getBoolean(KEY_INLINE_IMAGES, true))
    val inlineImagesEnabled: StateFlow<Boolean> = _inlineImagesEnabled.asStateFlow()

    fun setInlineImagesEnabled(enabled: Boolean) {
        _inlineImagesEnabled.value = enabled
        prefs.edit().putBoolean(KEY_INLINE_IMAGES, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "reader_settings"
        private const val KEY_INLINE_IMAGES = "inline_images_enabled"
    }
}
