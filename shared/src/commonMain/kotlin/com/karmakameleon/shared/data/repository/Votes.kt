package com.karmakameleon.shared.data.repository

// A vote is a direction (1 up, -1 down, 0 none); a post or comment stores the user's vote as likes (true, false or null).

internal fun voteToLikes(direction: Int): Boolean? =
    when (direction) {
        1 -> true
        -1 -> false
        else -> null
    }

internal fun likesToVote(likes: Boolean?): Int =
    when (likes) {
        true -> 1
        false -> -1
        null -> 0
    }
