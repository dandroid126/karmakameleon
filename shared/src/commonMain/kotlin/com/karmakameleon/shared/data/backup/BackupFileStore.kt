package com.karmakameleon.shared.data.backup

import com.karmakameleon.shared.util.runSuspendCatching
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Reads and writes export files, and keeps the list of those the app wrote or imported, so they can be deleted later. */
class BackupFileStore(
    private val settings: Settings,
    private val backupFiles: BackupFiles,
) {
    private val _files = MutableStateFlow<List<BackupFileInfo>>(emptyList())

    /** The listed files, newest first. Empty until [refresh] loads them. */
    val files: StateFlow<List<BackupFileInfo>> = _files.asStateFlow()

    suspend fun write(
        location: String,
        text: String,
    ) = backupFiles.write(location, text)

    suspend fun read(location: String): String = backupFiles.read(location)

    /** Adds the file to the list, keeping the app's access to it. Throws when the access can't be kept. */
    suspend fun remember(location: String) {
        backupFiles.keepAccess(location)
        saveLocations(storedLocations() + location)
        refresh()
    }

    /** Loads the details of the listed files, and drops those that are gone. */
    suspend fun refresh() {
        val found = mutableListOf<BackupFileInfo>()
        for (location in storedLocations()) {
            val info = runSuspendCatching { backupFiles.info(location) }.getOrNull()
            if (info != null) {
                found += info
            } else {
                runSuspendCatching { backupFiles.releaseAccess(location) }
            }
        }
        saveLocations(found.map { it.location })
        _files.value = found.sortedByDescending { it.lastModifiedMillis ?: 0 }
    }

    /** Deletes the file and drops it from the list. Throws when it can't be deleted, which leaves it listed. */
    suspend fun delete(location: String) {
        backupFiles.delete(location)
        runSuspendCatching { backupFiles.releaseAccess(location) }
        saveLocations(storedLocations() - location)
        _files.value = _files.value.filterNot { it.location == location }
    }

    // Content URIs can hold commas, but never line breaks.
    private fun storedLocations(): List<String> =
        settings
            .getStringOrNull(KEY_BACKUP_FILES)
            .orEmpty()
            .split("\n")
            .filter { it.isNotBlank() }

    private fun saveLocations(locations: List<String>) {
        settings[KEY_BACKUP_FILES] = locations.distinct().joinToString("\n")
    }

    private companion object {
        const val KEY_BACKUP_FILES = "backup_files"
    }
}
