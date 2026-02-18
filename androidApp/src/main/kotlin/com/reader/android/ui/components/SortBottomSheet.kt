package com.reader.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import com.reader.shared.domain.model.PostSort
import com.reader.shared.domain.model.TimeFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    currentSort: PostSort,
    currentTimeFilter: TimeFilter,
    onSortSelected: (PostSort) -> Unit,
    onTimeFilterSelected: (TimeFilter) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var showTimeFilter by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            if (!showTimeFilter) {
                Text(
                    text = "Sort by",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                SortOption(
                    icon = Icons.Default.Whatshot,
                    text = "Hot",
                    selected = currentSort == PostSort.HOT,
                    onClick = {
                        onSortSelected(PostSort.HOT)
                        onDismiss()
                    }
                )
                SortOption(
                    icon = Icons.Default.NewReleases,
                    text = "New",
                    selected = currentSort == PostSort.NEW,
                    onClick = {
                        onSortSelected(PostSort.NEW)
                        onDismiss()
                    }
                )
                SortOption(
                    icon = Icons.AutoMirrored.Default.TrendingUp,
                    text = "Top",
                    selected = currentSort == PostSort.TOP,
                    showArrow = true,
                    onClick = {
                        onSortSelected(PostSort.TOP)
                        showTimeFilter = true
                    }
                )
                SortOption(
                    icon = Icons.Default.ArrowUpward,
                    text = "Rising",
                    selected = currentSort == PostSort.RISING,
                    onClick = {
                        onSortSelected(PostSort.RISING)
                        onDismiss()
                    }
                )
                SortOption(
                    icon = Icons.Default.Forum,
                    text = "Controversial",
                    selected = currentSort == PostSort.CONTROVERSIAL,
                    showArrow = true,
                    onClick = {
                        onSortSelected(PostSort.CONTROVERSIAL)
                        showTimeFilter = true
                    }
                )
            } else {
                Text(
                    text = "Time Filter",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                TimeFilterOption(
                    text = "Now",
                    selected = currentTimeFilter == TimeFilter.HOUR,
                    onClick = {
                        onTimeFilterSelected(TimeFilter.HOUR)
                        onDismiss()
                    }
                )
                TimeFilterOption(
                    text = "Today",
                    selected = currentTimeFilter == TimeFilter.DAY,
                    onClick = {
                        onTimeFilterSelected(TimeFilter.DAY)
                        onDismiss()
                    }
                )
                TimeFilterOption(
                    text = "This Week",
                    selected = currentTimeFilter == TimeFilter.WEEK,
                    onClick = {
                        onTimeFilterSelected(TimeFilter.WEEK)
                        onDismiss()
                    }
                )
                TimeFilterOption(
                    text = "This Month",
                    selected = currentTimeFilter == TimeFilter.MONTH,
                    onClick = {
                        onTimeFilterSelected(TimeFilter.MONTH)
                        onDismiss()
                    }
                )
                TimeFilterOption(
                    text = "This Year",
                    selected = currentTimeFilter == TimeFilter.YEAR,
                    onClick = {
                        onTimeFilterSelected(TimeFilter.YEAR)
                        onDismiss()
                    }
                )
                TimeFilterOption(
                    text = "All Time",
                    selected = currentTimeFilter == TimeFilter.ALL,
                    onClick = {
                        onTimeFilterSelected(TimeFilter.ALL)
                        onDismiss()
                    }
                )
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
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary 
                       else MaterialTheme.colorScheme.onSurface
            )
        },
        trailingContent = if (showArrow) {
            { Icon(Icons.Default.ChevronRight, contentDescription = null) }
        } else if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else null,
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            headlineColor = if (selected) MaterialTheme.colorScheme.primary 
                           else MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun TimeFilterOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(text) },
        trailingContent = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else null,
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            headlineColor = if (selected) MaterialTheme.colorScheme.primary 
                           else MaterialTheme.colorScheme.onSurface
        )
    )
}
