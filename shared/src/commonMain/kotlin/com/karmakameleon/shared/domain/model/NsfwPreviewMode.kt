package com.karmakameleon.shared.domain.model

enum class NsfwPreviewMode(val displayName: String) {
    DO_NOT_PREFETCH("Do not prefetch"),
    PREFETCH_AND_BLUR("Prefetch and blur"),
    SHOW_PREVIEWS("Show previews")
}
