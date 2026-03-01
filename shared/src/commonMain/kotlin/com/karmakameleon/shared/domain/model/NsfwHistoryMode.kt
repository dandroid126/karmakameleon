package com.karmakameleon.shared.domain.model

enum class NsfwHistoryMode(val displayName: String) {
    SAVE_ALL("Save history for all posts"),
    DONT_SAVE_NSFW_SUBREDDITS("Don't save history in NSFW subreddits only"),
    DONT_SAVE_ANY_NSFW("Don't save history on any NSFW post")
}
