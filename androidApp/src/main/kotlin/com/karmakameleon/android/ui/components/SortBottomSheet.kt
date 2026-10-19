package com.karmakameleon.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.karmakameleon.shared.domain.model.PostSort
import com.karmakameleon.shared.domain.model.TimeFilter

// Top and Controversial go on to ask for a time filter; the other sorts close the sheet.
private class SortChoice(
    val sort: PostSort,
    val icon: ImageVector,
    val label: String,
) {
    val needsTimeFilter: Boolean get() = sort == PostSort.TOP || sort == PostSort.CONTROVERSIAL
}

private val sortChoices =
    listOf(
        SortChoice(PostSort.HOT, Icons.Default.Whatshot, "Hot"),
        SortChoice(PostSort.NEW, Icons.Default.NewReleases, "New"),
        SortChoice(PostSort.TOP, Icons.AutoMirrored.Default.TrendingUp, "Top"),
        SortChoice(PostSort.RISING, Icons.Default.ArrowUpward, "Rising"),
        SortChoice(PostSort.CONTROVERSIAL, Icons.Default.Forum, "Controversial"),
    )

private val timeFilterLabels =
    listOf(
        TimeFilter.HOUR to "Now",
        TimeFilter.DAY to "Today",
        TimeFilter.WEEK to "This Week",
        TimeFilter.MONTH to "This Month",
        TimeFilter.YEAR to "This Year",
        TimeFilter.ALL to "All Time",
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    currentSort: PostSort,
    currentTimeFilter: TimeFilter,
    onSortSelected: (PostSort) -> Unit,
    onTimeFilterSelected: (TimeFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var showTimeFilter by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
        ) {
            Text(
                text = if (showTimeFilter) "Time Filter" else "Sort by",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (!showTimeFilter) {
                sortChoices.forEach { choice ->
                    SortOption(
                        icon = choice.icon,
                        text = choice.label,
                        selected = currentSort == choice.sort,
                        showArrow = choice.needsTimeFilter,
                        onClick = {
                            onSortSelected(choice.sort)
                            if (choice.needsTimeFilter) showTimeFilter = true else onDismiss()
                        },
                    )
                }
            } else {
                timeFilterLabels.forEach { (timeFilter, label) ->
                    TimeFilterOption(
                        text = label,
                        selected = currentTimeFilter == timeFilter,
                        onClick = {
                            onTimeFilterSelected(timeFilter)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SortOption(
    icon: ImageVector,
    text: String,
    selected: Boolean,
    showArrow: Boolean = false,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        },
        trailingContent =
            if (showArrow) {
                { Icon(Icons.Default.ChevronRight, contentDescription = null) }
            } else if (selected) {
                { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            } else {
                null
            },
        modifier = Modifier.clickable(onClick = onClick),
        colors =
            ListItemDefaults.colors(
                headlineColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            ),
    )
}

@Composable
private fun TimeFilterOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(text) },
        trailingContent =
            if (selected) {
                { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            } else {
                null
            },
        modifier = Modifier.clickable(onClick = onClick),
        colors =
            ListItemDefaults.colors(
                headlineColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            ),
    )
}
