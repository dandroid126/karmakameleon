package com.karmakameleon.android.ui.profile

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karmakameleon.android.navigation.NavigationHandler
import com.karmakameleon.android.ui.components.CommentItem
import com.karmakameleon.android.ui.components.PostActions
import com.karmakameleon.android.ui.components.PostListItem
import com.karmakameleon.android.ui.components.rememberCompactPostSelection
import com.karmakameleon.shared.data.repository.ReadPostsRepository
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.domain.model.NsfwHistoryMode
import com.karmakameleon.shared.domain.model.NsfwPreviewMode
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.ui.profile.SavedContentType
import kotlinx.coroutines.launch

// A menu to switch between saved posts and saved comments, then the chosen list.
@Composable
internal fun ProfileSavedTab(
    savedContentType: SavedContentType,
    onSetSavedContentType: (SavedContentType) -> Unit,
    postsContent: @Composable () -> Unit,
    commentsContent: @Composable () -> Unit,
) {
    var showSavedTypeMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { showSavedTypeMenu = true },
            ) {
                Text(
                    savedContentType.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Change saved type")
            }
            DropdownMenu(
                expanded = showSavedTypeMenu,
                onDismissRequest = { showSavedTypeMenu = false },
            ) {
                SavedContentType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.displayName) },
                        onClick = {
                            onSetSavedContentType(type)
                            showSavedTypeMenu = false
                        },
                    )
                }
            }
        }

        when (savedContentType) {
            SavedContentType.POSTS -> postsContent()
            SavedContentType.COMMENTS -> commentsContent()
        }
    }
}
