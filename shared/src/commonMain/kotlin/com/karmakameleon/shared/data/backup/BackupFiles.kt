package com.karmakameleon.shared.data.backup

/**
 * Reads and writes export files where the user picked them. A location is whatever the platform's file picker returns,
 * like an Android content URI. Each method throws when the file can't be reached.
 */
interface BackupFiles {
    suspend fun write(
        location: String,
        text: String,
    )

    suspend fun read(location: String): String

    /** Keeps the app's access to the file after it restarts, so the settings can list and delete it. */
    fun keepAccess(location: String)

    fun releaseAccess(location: String)

    /** Null when the file is gone or the app can no longer reach it. */
    suspend fun info(location: String): BackupFileInfo?

    suspend fun delete(location: String)
}
