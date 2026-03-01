package com.karmakameleon.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.karmakameleon.android.ui.menu.GlobalMenuManager
import com.karmakameleon.android.ui.menu.OverflowMenuItem
import com.karmakameleon.shared.data.repository.UserRepository
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalTopAppBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
    currentRoute: String? = null,
    excludeProfile: Boolean = false,
    excludeInbox: Boolean = false,
    excludeSettings: Boolean = false,
    globalMenuManager: GlobalMenuManager = koinInject(),
    userRepository: UserRepository = koinInject(),
) {
    val currentAccount by userRepository.currentAccount.collectAsState()
    val isLoggedIn by userRepository.isLoggedIn.collectAsState()
    var showOverflowMenu by remember { mutableStateOf(false) }

    val baseItems = buildList {
        if (!excludeProfile) {
            when {
                !isLoggedIn -> add(OverflowMenuItem(
                    title = "Log In",
                    onClick = { globalMenuManager.onNavigateToOwnProfile() }
                ))
                currentAccount != null -> add(OverflowMenuItem(
                    title = "${currentAccount!!.name}'s Profile",
                    onClick = { globalMenuManager.onNavigateToProfile(currentAccount!!.name) }
                ))
                else -> add(OverflowMenuItem(
                    title = "Profile",
                    onClick = { globalMenuManager.onNavigateToOwnProfile() }
                ))
            }
        }
        if (!excludeInbox && isLoggedIn) {
            add(OverflowMenuItem(title = "Inbox", onClick = { globalMenuManager.onNavigateToInbox() }))
        }
        if (!excludeSettings) {
            add(OverflowMenuItem(title = "Settings", onClick = { globalMenuManager.onNavigateToSettings() }))
        }
    }

    val screenItems = globalMenuManager.getScreenItems()

    TopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = {
            actions()
            IconButton(onClick = { showOverflowMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(
                expanded = showOverflowMenu,
                onDismissRequest = { showOverflowMenu = false }
            ) {
                screenItems.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.title) },
                        leadingIcon = item.icon?.let { icon ->
                            { Icon(icon, contentDescription = null) }
                        },
                        onClick = {
                            showOverflowMenu = false
                            item.onClick()
                        }
                    )
                }
                baseItems.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.title) },
                        leadingIcon = item.icon?.let { icon ->
                            { Icon(icon, contentDescription = null) }
                        },
                        onClick = {
                            showOverflowMenu = false
                            item.onClick()
                        }
                    )
                }
            }
        }
    )
}
