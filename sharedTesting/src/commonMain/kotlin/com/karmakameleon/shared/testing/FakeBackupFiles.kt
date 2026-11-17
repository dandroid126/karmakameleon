package com.karmakameleon.shared.testing

import com.karmakameleon.shared.data.backup.BackupFileInfo
import com.karmakameleon.shared.data.backup.BackupFiles

/**
 * Export files kept in memory, by location. A file's name is the end of its location, its size the length of its text,
 * and each file written is a minute newer than the one before, starting at [FIRST_WRITE_MILLIS].
 */
class FakeBackupFiles : BackupFiles {
    val contents = mutableMapOf<String, String>()
    private val writeOrder = mutableMapOf<String, Long>()
    private var writes = 0L

    /** The locations the app keeps access to. */
    val kept = mutableSetOf<String>()

    /** Locations that can't be written, read or deleted, like a file on a removed SD card. */
    val failing = mutableSetOf<String>()

    override suspend fun write(
        location: String,
        text: String,
    ) {
        check(location !in failing) { "Can't write $location" }
        contents[location] = text
        writeOrder[location] = ++writes
    }

    override suspend fun read(location: String): String {
        check(location !in failing) { "Can't read $location" }
        return contents[location] ?: error("No file at $location")
    }

    /** Locations the app can't keep access to, like a file from a provider that doesn't allow it. */
    val notKeepable = mutableSetOf<String>()

    override fun keepAccess(location: String) {
        check(location !in notKeepable) { "Can't keep access to $location" }
        kept += location
    }

    override fun releaseAccess(location: String) {
        kept -= location
    }

    override suspend fun info(location: String): BackupFileInfo? {
        val text = contents[location] ?: return null
        val lastModified = writeOrder[location]?.let { FIRST_WRITE_MILLIS + (it - 1) * MINUTE_MILLIS }
        return BackupFileInfo(location, location.substringAfterLast('/'), text.length.toLong(), lastModified)
    }

    override suspend fun delete(location: String) {
        check(location !in failing) { "Can't delete $location" }
        checkNotNull(contents.remove(location)) { "No file at $location" }
    }

    companion object {
        /** 14 January 2027, 8:00 UTC. */
        const val FIRST_WRITE_MILLIS = 1_799_913_600_000L
        private const val MINUTE_MILLIS = 60_000L
    }
}
