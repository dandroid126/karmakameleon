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
import androidx.compose.ui.Modifier
import com.karmakameleon.android.ui.menu.GlobalMenuManager
import com.karmakameleon.android.ui.menu.OverflowMenuItem
import com.karmakameleon.shared.data.repository.UserRepository
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
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

    val menuItems =
        globalMenuManager.getScreenItems() +
            globalMenuManager.appMenuItems(isLoggedIn, currentAccount?.name, excludeProfile, excludeInbox, excludeSettings)

    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = {
            actions()
            IconButton(onClick = { showOverflowMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(
                expanded = showOverflowMenu,
                onDismissRequest = { showOverflowMenu = false },
            ) {
                menuItems.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.title) },
                        leadingIcon =
                            item.icon?.let { icon ->
                                { Icon(icon, contentDescription = null) }
                            },
                        onClick = {
                            showOverflowMenu = false
                            item.onClick()
                        },
                    )
                }
            }
        },
    )
}

// The overflow menu's app-wide items: your profile (or logging in), the inbox and settings, minus the screen's own.
private fun GlobalMenuManager.appMenuItems(
    isLoggedIn: Boolean,
    accountName: String?,
    excludeProfile: Boolean,
    excludeInbox: Boolean,
    excludeSettings: Boolean,
): List<OverflowMenuItem> =
    buildList {
        if (!excludeProfile) {
            add(
                when {
                    !isLoggedIn -> {
                        OverflowMenuItem(title = "Log In", onClick = { onNavigateToOwnProfile() })
                    }

                    accountName != null -> {
                        OverflowMenuItem(
                            title = "$accountName's Profile",
                            onClick = { onNavigateToProfile(accountName) },
                        )
                    }

                    else -> {
                        OverflowMenuItem(title = "Profile", onClick = { onNavigateToOwnProfile() })
                    }
                },
            )
        }
        if (!excludeInbox && isLoggedIn) {
            add(OverflowMenuItem(title = "Inbox", onClick = { onNavigateToInbox() }))
        }
        if (!excludeSettings) {
            add(OverflowMenuItem(title = "Settings", onClick = { onNavigateToSettings() }))
        }
    }
