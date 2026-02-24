package com.reader.android.ui.settings

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
import com.reader.android.notifications.InboxNotificationWorker
import com.reader.shared.data.repository.ReadPostsRepository
import com.reader.shared.data.repository.SettingsRepository
import com.reader.shared.domain.model.NotificationInterval
import com.reader.shared.domain.model.NsfwHistoryMode
import com.reader.shared.domain.model.NsfwPreviewMode
import com.reader.android.ui.components.UniversalTopAppBar
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    currentRoute: String? = null,
    onBackClick: () -> Unit,
    settingsRepository: SettingsRepository = koinInject(),
    readPostsRepository: ReadPostsRepository = koinInject()
) {
    val inlineImagesEnabled by settingsRepository.inlineImagesEnabled.collectAsState()
    val notificationInterval by settingsRepository.notificationInterval.collectAsState()
    val blockedSubreddits by settingsRepository.blockedSubreddits.collectAsState()
    val nsfwEnabled by settingsRepository.nsfwEnabled.collectAsState()
    val nsfwCacheMedia by settingsRepository.nsfwCacheMedia.collectAsState()
    val nsfwPreviewMode by settingsRepository.nsfwPreviewMode.collectAsState()
    val nsfwSearchEnabled by settingsRepository.nsfwSearchEnabled.collectAsState()
    val nsfwHistoryMode by settingsRepository.nsfwHistoryMode.collectAsState()
    val spoilerPreviewsEnabled by settingsRepository.spoilerPreviewsEnabled.collectAsState()
    val context = LocalContext.current
    var showPurgeConfirmDialog by remember { mutableStateOf(false) }

    if (showPurgeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmDialog = false },
            title = { Text("Clear NSFW Read History") },
            text = { Text("This will mark all NSFW posts and posts from NSFW subreddits as unread. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        readPostsRepository.purgeNsfwReadPosts()
                        showPurgeConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            UniversalTopAppBar(
                currentRoute = currentRoute,
                excludeSettings = true,
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Inline Images",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Render images and GIFs inline in comments and post text",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = inlineImagesEnabled,
                        onCheckedChange = { settingsRepository.setInlineImagesEnabled(it) }
                    )
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Inbox Notification Interval",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "How often to check for new inbox messages in the background",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowColumn {
                        NotificationInterval.entries.forEach { interval ->
                            FilterChip(
                                selected = notificationInterval == interval,
                                onClick = {
                                    settingsRepository.setNotificationInterval(interval)
                                    InboxNotificationWorker.schedule(context)
                                },
                                label = { Text(interval.displayName) }
                            )
                        }
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // NSFW Settings
            item {
                Text(
                    text = "NSFW Content",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable NSFW Content",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Allow loading and viewing NSFW posts and subreddits",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = nsfwEnabled,
                        onCheckedChange = { settingsRepository.setNsfwEnabled(it) }
                    )
                }
            }

            item {
                Button(
                    onClick = { showPurgeConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Clear NSFW Read History")
                }
            }

            if (nsfwEnabled) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cache NSFW Media",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Allow caching images and videos from NSFW posts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = nsfwCacheMedia,
                            onCheckedChange = { settingsRepository.setNsfwCacheMedia(it) }
                        )
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "NSFW Preview Mode",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "How to display NSFW image and video previews in feeds",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowColumn {
                            NsfwPreviewMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = nsfwPreviewMode == mode,
                                    onClick = { settingsRepository.setNsfwPreviewMode(mode) },
                                    label = { Text(mode.displayName) }
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show NSFW Subreddits in Search",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Include NSFW subreddits in subreddit search results",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = nsfwSearchEnabled,
                            onCheckedChange = { settingsRepository.setNsfwSearchEnabled(it) }
                        )
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "NSFW Post History",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Whether to save read history for NSFW posts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowColumn {
                            NsfwHistoryMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = nsfwHistoryMode == mode,
                                    onClick = { settingsRepository.setNsfwHistoryMode(mode) },
                                    label = { Text(mode.displayName) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Spoiler Settings
            item {
                Text(
                    text = "Spoiler Content",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show Spoiler Previews",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Show image and video previews for spoiler posts in feeds",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = spoilerPreviewsEnabled,
                        onCheckedChange = { settingsRepository.setSpoilerPreviewsEnabled(it) }
                    )
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Blocked Subreddits",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Posts from these subreddits will be hidden from all feeds",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (blockedSubreddits.isEmpty()) {
                item {
                    Text(
                        text = "No blocked subreddits",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                item {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        blockedSubreddits.sorted().forEach { subreddit ->
                            InputChip(
                                selected = false,
                                onClick = { settingsRepository.removeBlockedSubreddit(subreddit) },
                                label = { Text("r/$subreddit") },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Unblock"
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
