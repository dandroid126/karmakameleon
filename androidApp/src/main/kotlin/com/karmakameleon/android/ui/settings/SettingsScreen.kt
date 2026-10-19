package com.karmakameleon.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karmakameleon.android.notifications.InboxNotificationWorker
import com.karmakameleon.android.ui.components.UniversalTopAppBar
import com.karmakameleon.shared.data.repository.ReadPostsRepository
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.domain.model.NotificationInterval
import com.karmakameleon.shared.domain.model.NsfwHistoryMode
import com.karmakameleon.shared.domain.model.NsfwPreviewMode
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.koinInject

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentRoute: String? = null,
    settingsRepository: SettingsRepository = koinInject(),
    readPostsRepository: ReadPostsRepository = koinInject(),
) {
    val nsfwEnabled by settingsRepository.nsfwEnabled.collectAsState()
    val blockedSubreddits by settingsRepository.blockedSubreddits.collectAsState()
    val context = LocalContext.current
    var showPurgeConfirmDialog by remember { mutableStateOf(false) }

    if (showPurgeConfirmDialog) {
        PurgeNsfwHistoryDialog(
            onConfirm = {
                readPostsRepository.purgeNsfwReadPosts()
                showPurgeConfirmDialog = false
            },
            onDismiss = { showPurgeConfirmDialog = false },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            UniversalTopAppBar(
                currentRoute = currentRoute,
                excludeSettings = true,
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            generalSettings(settingsRepository, onIntervalChange = { InboxNotificationWorker.schedule(context) })
            nsfwSettings(settingsRepository, nsfwEnabled = nsfwEnabled, onClearHistoryClick = { showPurgeConfirmDialog = true })
            spoilerSettings(settingsRepository)
            blockedSubredditSettings(blockedSubreddits, onUnblock = settingsRepository::removeBlockedSubreddit)
        }
    }
}

// The notification worker is rescheduled whenever the interval changes.
private fun LazyListScope.generalSettings(
    settingsRepository: SettingsRepository,
    onIntervalChange: () -> Unit,
) {
    item {
        SwitchSetting(
            title = "Inline Images",
            description = "Render images and GIFs inline in comments and post text",
            state = settingsRepository.inlineImagesEnabled,
            onCheckedChange = settingsRepository::setInlineImagesEnabled,
        )
    }
    item {
        SwitchSetting(
            title = "Compact Feed",
            description =
                "Show posts as compact cards with small thumbnails. Tap a post for its controls, " +
                    "or tap the thumbnail to open it",
            state = settingsRepository.compactFeedEnabled,
            onCheckedChange = settingsRepository::setCompactFeedEnabled,
        )
    }
    item { SettingsDivider() }
    item {
        ChoiceSetting(
            title = "Inbox Notification Interval",
            description = "How often to check for new inbox messages in the background",
            options = NotificationInterval.entries,
            state = settingsRepository.notificationInterval,
            label = { it.displayName },
            onSelect = {
                settingsRepository.setNotificationInterval(it)
                onIntervalChange()
            },
        )
    }
    item { SettingsDivider() }
}

// The other NSFW options only show while NSFW content is enabled.
private fun LazyListScope.nsfwSettings(
    settingsRepository: SettingsRepository,
    nsfwEnabled: Boolean,
    onClearHistoryClick: () -> Unit,
) {
    item { SettingsSectionHeader("NSFW Content") }
    item {
        SwitchSetting(
            title = "Enable NSFW Content",
            description = "Allow loading and viewing NSFW posts and subreddits",
            state = settingsRepository.nsfwEnabled,
            onCheckedChange = settingsRepository::setNsfwEnabled,
        )
    }
    item { ClearNsfwHistoryButton(onClick = onClearHistoryClick) }
    if (nsfwEnabled) {
        item {
            SwitchSetting(
                title = "Cache NSFW Media",
                description = "Allow caching images and videos from NSFW posts",
                state = settingsRepository.nsfwCacheMedia,
                onCheckedChange = settingsRepository::setNsfwCacheMedia,
            )
        }
        item {
            ChoiceSetting(
                title = "NSFW Preview Mode",
                description = "How to display NSFW image and video previews in feeds",
                options = NsfwPreviewMode.entries,
                state = settingsRepository.nsfwPreviewMode,
                label = { it.displayName },
                onSelect = settingsRepository::setNsfwPreviewMode,
            )
        }
        item {
            SwitchSetting(
                title = "Show NSFW Subreddits in Search",
                description = "Include NSFW subreddits in subreddit search results",
                state = settingsRepository.nsfwSearchEnabled,
                onCheckedChange = settingsRepository::setNsfwSearchEnabled,
            )
        }
        item {
            ChoiceSetting(
                title = "NSFW Post History",
                description = "Whether to save read history for NSFW posts",
                options = NsfwHistoryMode.entries,
                state = settingsRepository.nsfwHistoryMode,
                label = { it.displayName },
                onSelect = settingsRepository::setNsfwHistoryMode,
            )
        }
    }
    item { SettingsDivider() }
}

private fun LazyListScope.spoilerSettings(settingsRepository: SettingsRepository) {
    item { SettingsSectionHeader("Spoiler Content") }
    item {
        SwitchSetting(
            title = "Show Spoiler Previews",
            description = "Show image and video previews for spoiler posts in feeds",
            state = settingsRepository.spoilerPreviewsEnabled,
            onCheckedChange = settingsRepository::setSpoilerPreviewsEnabled,
        )
    }
    item { SettingsDivider() }
}

// Tapping a blocked subreddit unblocks it.
@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.blockedSubredditSettings(
    blockedSubreddits: Set<String>,
    onUnblock: (String) -> Unit,
) {
    item { SettingTitle("Blocked Subreddits", "Posts from these subreddits will be hidden from all feeds") }
    item {
        if (blockedSubreddits.isEmpty()) {
            Text(
                text = "No blocked subreddits",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            FlowRow(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                blockedSubreddits.sorted().forEach { subreddit ->
                    InputChip(
                        selected = false,
                        onClick = { onUnblock(subreddit) },
                        label = { Text("r/$subreddit") },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Unblock") },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun SettingTitle(
    title: String,
    description: String,
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
) {
    Column(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    description: String,
    state: StateFlow<Boolean>,
    onCheckedChange: (Boolean) -> Unit,
) {
    val checked by state.collectAsState()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingTitle(title = title, description = description, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// A setting with a few options, shown as chips in a column.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceSetting(
    title: String,
    description: String,
    options: List<T>,
    state: StateFlow<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val selected by state.collectAsState()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingTitle(title = title, description = description, modifier = Modifier)
        FlowColumn {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

@Composable
private fun ClearNsfwHistoryButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
    ) {
        Text("Clear NSFW Read History")
    }
}

@Composable
private fun PurgeNsfwHistoryDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear NSFW Read History") },
        text = { Text("This will mark all NSFW posts and posts from NSFW subreddits as unread. This cannot be undone.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Clear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
