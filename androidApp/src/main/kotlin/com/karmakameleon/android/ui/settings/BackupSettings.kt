package com.karmakameleon.android.ui.settings

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.karmakameleon.shared.data.backup.Backup
import com.karmakameleon.shared.data.backup.BackupCategory
import com.karmakameleon.shared.data.backup.BackupFileInfo
import com.karmakameleon.shared.data.repository.BackupRepository
import com.karmakameleon.shared.data.repository.ImportResult
import com.karmakameleon.shared.data.repository.InvalidBackupException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Read history and drafts are more personal, so they're only exported when asked for.
private val defaultExportCategories = BackupCategory.entries.toSet() - BackupCategory.READ_HISTORY - BackupCategory.DRAFTS

// Android's file picker matches these against what the file's storage says it is, which isn't always JSON.
private val importTypes = arrayOf("application/json", "text/plain", "application/octet-stream")

/** The export file's suggested name. The date keeps one export from replacing another. */
internal fun exportFileName(date: Date): String = "karmakameleon-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)}.json"

internal fun BackupCategory.description(): String =
    when (this) {
        BackupCategory.SETTINGS -> "The options on this page, including the notification interval"
        BackupCategory.BLOCKED_SUBREDDITS -> "Subreddits hidden from your feeds"
        BackupCategory.FAVORITE_SUBREDDITS -> "Subreddits starred in your subreddit list"
        BackupCategory.READ_HISTORY -> "Which posts you've opened"
        BackupCategory.DRAFTS -> "Comments and replies you haven't sent yet"
        BackupCategory.REDDIT_APP -> "The client ID and redirect URI you log in with"
    }

/** A dialog the export and import section shows over the settings. */
internal sealed interface BackupDialog {
    data object Export : BackupDialog

    data class ConfirmImport(
        val location: String,
        val backup: Backup,
    ) : BackupDialog

    data class Imported(
        val location: String,
        val categories: Set<BackupCategory>,
        val result: ImportResult,
    ) : BackupDialog

    data class ConfirmDelete(
        val file: BackupFileInfo,
    ) : BackupDialog

    data class Message(
        val title: String,
        val text: String,
    ) : BackupDialog
}

/** Runs the export, import and delete flows, which go through Android's file pickers and a few dialogs. */
@Stable
internal class BackupController(
    private val repository: BackupRepository,
    private val scope: CoroutineScope,
    private val snackbarHostState: SnackbarHostState,
    private val onImport: (ImportResult) -> Unit,
) {
    var dialog by mutableStateOf<BackupDialog?>(null)
    var exportCategories by mutableStateOf(defaultExportCategories)

    // Set by rememberBackupController, since the pickers can only be made while composing.
    internal var pickExportFile: (String) -> Unit = {}
    internal var pickImportFile: () -> Unit = {}

    val files get() = repository.files

    suspend fun refreshFiles() = repository.refreshFiles()

    fun startExport() {
        dialog = BackupDialog.Export
    }

    /** Categories with nothing to export: the Reddit app setup before a client ID is set. */
    val unavailableExportCategories: Set<BackupCategory>
        get() = if (repository.hasRedditApp()) emptySet() else setOf(BackupCategory.REDDIT_APP)

    // The choice is kept as made, so a category that becomes available later is still ticked.
    val chosenExportCategories: Set<BackupCategory> get() = exportCategories - unavailableExportCategories

    fun confirmExport() {
        dialog = null
        pickExportFile(exportFileName(Date()))
    }

    fun onExportFilePicked(location: String?) {
        location ?: return
        val categories = chosenExportCategories
        scope.launch {
            val message =
                repository.export(location, categories).fold(
                    onSuccess = { "Exported ${nameOf(location) ?: "the file"}" },
                    onFailure = { "Couldn't export: ${it.message}" },
                )
            snackbarHostState.showSnackbar(message)
        }
    }

    fun startImport() {
        pickImportFile()
    }

    fun onImportFilePicked(location: String?) {
        location ?: return
        scope.launch {
            dialog =
                repository.read(location).fold(
                    onSuccess = { backup ->
                        if (backup.categories.isEmpty()) {
                            BackupDialog.Message("Nothing to Import", "This export file is empty.")
                        } else {
                            BackupDialog.ConfirmImport(location, backup)
                        }
                    },
                    onFailure = {
                        val text =
                            if (it is InvalidBackupException) {
                                "This file isn't a Karma Kameleon export."
                            } else {
                                "The file couldn't be read: ${it.message}"
                            }
                        BackupDialog.Message("Can't Import", text)
                    },
                )
        }
    }

    fun importLogsOut(backup: Backup): Boolean = repository.importLogsOut(backup)

    fun confirmImport(
        dialog: BackupDialog.ConfirmImport,
        categories: Set<BackupCategory>,
    ) {
        this.dialog = null
        scope.launch {
            val result = repository.import(dialog.location, dialog.backup, categories)
            onImport(result)
            this@BackupController.dialog = BackupDialog.Imported(dialog.location, categories, result)
        }
    }

    fun confirmDelete(file: BackupFileInfo) {
        dialog = BackupDialog.ConfirmDelete(file)
    }

    fun delete(location: String) {
        val name = nameOf(location) ?: "the file"
        dialog = null
        scope.launch {
            val message =
                repository.delete(location).fold(
                    onSuccess = { "Deleted $name" },
                    onFailure = { "Couldn't delete $name: ${it.message}" },
                )
            snackbarHostState.showSnackbar(message)
        }
    }

    fun nameOf(location: String): String? =
        repository.files.value
            .firstOrNull { it.location == location }
            ?.name
}

@Composable
internal fun rememberBackupController(
    repository: BackupRepository,
    snackbarHostState: SnackbarHostState,
    onImport: (ImportResult) -> Unit,
): BackupController {
    val scope = rememberCoroutineScope()
    val controller = remember(repository) { BackupController(repository, scope, snackbarHostState, onImport) }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
            controller.onExportFilePicked(it?.toString())
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            controller.onImportFilePicked(it?.toString())
        }
    controller.pickExportFile = exportLauncher::launch
    controller.pickImportFile = { importLauncher.launch(importTypes) }
    return controller
}

// The export and import buttons, then the files they made.
internal fun LazyListScope.backupSettings(
    files: List<BackupFileInfo>,
    controller: BackupController,
) {
    item { SettingsSectionHeader("Export and Import") }
    item {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = controller::startExport, modifier = Modifier.weight(1f)) { Text("Export") }
            OutlinedButton(onClick = controller::startImport, modifier = Modifier.weight(1f)) { Text("Import") }
        }
    }
    item {
        SettingTitle(
            title = "Export Files",
            description = "Files exported or imported here. Deleting one deletes the file itself",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
    if (files.isEmpty()) {
        item {
            Text(
                text = "No export files",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    } else {
        items(files, key = { it.location }) { file ->
            BackupFileRow(file, onDelete = { controller.confirmDelete(file) })
        }
    }
}

@Composable
private fun BackupFileRow(
    file: BackupFileInfo,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val details =
        listOfNotNull(
            file.sizeBytes?.let { Formatter.formatShortFileSize(context, it) },
            file.lastModifiedMillis?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) },
        ).joinToString(" · ")
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingTitle(title = file.name, description = details, modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete ${file.name}")
        }
    }
}

@Composable
internal fun BackupDialogs(controller: BackupController) {
    val dialog = controller.dialog ?: return
    val onDismiss = { controller.dialog = null }
    when (dialog) {
        BackupDialog.Export -> {
            ExportDialog(controller, onDismiss)
        }

        is BackupDialog.ConfirmImport -> {
            ConfirmImportDialog(dialog, controller, onDismiss)
        }

        is BackupDialog.Imported -> {
            ImportedDialog(dialog, controller, onDismiss)
        }

        is BackupDialog.ConfirmDelete -> {
            ConfirmDeleteDialog(dialog.file.name, onConfirm = { controller.delete(dialog.file.location) }, onDismiss = onDismiss)
        }

        is BackupDialog.Message -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(dialog.title) },
                text = { Text(dialog.text) },
                confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
            )
        }
    }
}

@Composable
private fun ExportDialog(
    controller: BackupController,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                CategoryChecklist(
                    categories = BackupCategory.entries,
                    checked = controller.chosenExportCategories,
                    onCheckedChange = { controller.exportCategories = it },
                    disabled = controller.unavailableExportCategories,
                )
                Text(
                    text = "The file is plain text, so anyone who has it can read what's in it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Button(onClick = controller::confirmExport, enabled = controller.chosenExportCategories.isNotEmpty()) { Text("Export") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmImportDialog(
    dialog: BackupDialog.ConfirmImport,
    controller: BackupController,
    onDismiss: () -> Unit,
) {
    val inFile = BackupCategory.entries.filter { it in dialog.backup.categories }
    var chosen by remember(dialog) { mutableStateOf(inFile.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("These will be replaced with the file's:")
                CategoryChecklist(categories = inFile, checked = chosen, onCheckedChange = { chosen = it })
                val notes =
                    buildList {
                        add("Everything else stays as it is.")
                        if (BackupCategory.REDDIT_APP in chosen && controller.importLogsOut(dialog.backup)) {
                            add("The file has a different client ID, so you'll be logged out.")
                        }
                        if (dialog.backup.isFromNewerVersion) {
                            add("A newer version of the app made this file. What this version doesn't know is skipped.")
                        }
                    }
                notes.forEach {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { controller.confirmImport(dialog, chosen) },
                enabled = chosen.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Replace")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ImportedDialog(
    dialog: BackupDialog.Imported,
    controller: BackupController,
    onDismiss: () -> Unit,
) {
    val name = controller.nameOf(dialog.location)
    val text =
        buildList {
            add("Replaced " + BackupCategory.entries.filter { it in dialog.categories }.joinToString { it.displayName } + ".")
            if (dialog.result.loggedOut) add("The client ID changed, so you were logged out. Log in again from your profile.")
            if (dialog.result.skippedUnknownValues) add("Some of the file came from a newer version of the app and was skipped.")
            // The file is only listed when the app could keep access to it.
            if (name != null) add("You can delete $name now, or later from Export Files in the settings.")
        }.joinToString("\n\n")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Imported") },
        text = { Text(text) },
        confirmButton = { Button(onClick = onDismiss) { Text("Keep File") } },
        dismissButton = {
            if (name != null) TextButton(onClick = { controller.delete(dialog.location) }) { Text("Delete File") }
        },
    )
}

@Composable
private fun ConfirmDeleteDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete File?") },
        text = { Text("This deletes $name itself, not just its entry in this list.") },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Delete")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CategoryChecklist(
    categories: List<BackupCategory>,
    checked: Set<BackupCategory>,
    onCheckedChange: (Set<BackupCategory>) -> Unit,
    disabled: Set<BackupCategory> = emptySet(),
) {
    categories.forEach { category ->
        val isChecked = category in checked
        val enabled = category !in disabled
        // The whole row is the checkbox, like the settings' switches.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = isChecked,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = { onCheckedChange(if (it) checked + category else checked - category) },
                    ).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = isChecked, onCheckedChange = null, enabled = enabled)
            SettingTitle(
                title = category.displayName,
                description = if (enabled) category.description() else "Not set up on this device",
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
