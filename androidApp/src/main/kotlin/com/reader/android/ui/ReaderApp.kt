package com.reader.android.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.reader.android.ui.components.WebBrowserScreen
import com.reader.android.ui.feed.FeedScreen
import com.reader.android.ui.inbox.InboxScreen
import com.reader.android.ui.post.PostDetailScreen
import com.reader.android.ui.profile.ProfileScreen
import com.reader.android.ui.search.SearchScreen
import com.reader.android.ui.subreddit.SubredditScreen
import com.reader.android.ui.subreddit.SubredditListScreen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Feed : Screen("feed", "Feed", Icons.Filled.Home, Icons.Outlined.Home)
    data object Subreddits : Screen("subreddits", "Subreddits", Icons.Filled.List, Icons.Outlined.List)
    data object Search : Screen("search", "Search", Icons.Filled.Search, Icons.Outlined.Search)
    data object Inbox : Screen("inbox", "Inbox", Icons.Filled.Email, Icons.Outlined.Email)
    data object Profile : Screen("profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

sealed class DetailScreen(val route: String) {
    data object SubredditDetail : DetailScreen("subreddit/{subredditName}") {
        fun createRoute(subredditName: String) = "subreddit/$subredditName"
    }
    data object PostDetail : DetailScreen("post/{subreddit}/{postId}") {
        fun createRoute(subreddit: String, postId: String) = "post/$subreddit/$postId"
    }
    data object UserProfile : DetailScreen("user/{username}") {
        fun createRoute(username: String) = "user/$username"
    }
    data object WebBrowser : DetailScreen("web/{url}") {
        fun createRoute(url: String) = "web/${URLEncoder.encode(url, StandardCharsets.UTF_8.toString())}"
    }
}

val bottomNavItems = listOf(
    Screen.Feed,
    Screen.Subreddits,
    Screen.Search,
    Screen.Inbox,
    Screen.Profile
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    val showBottomBar = bottomNavItems.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Feed.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Feed.route) {
                FeedScreen(
                    onPostClick = { subreddit, postId ->
                        navController.navigate(DetailScreen.PostDetail.createRoute(subreddit, postId))
                    },
                    onSubredditClick = { subredditName ->
                        navController.navigate(DetailScreen.SubredditDetail.createRoute(subredditName))
                    },
                    onUserClick = { username ->
                        navController.navigate(DetailScreen.UserProfile.createRoute(username))
                    },
                    onLinkClick = { url ->
                        navController.navigate(DetailScreen.WebBrowser.createRoute(url))
                    }
                )
            }
            
            composable(Screen.Subreddits.route) {
                SubredditListScreen(
                    onSubredditClick = { subredditName ->
                        navController.navigate(DetailScreen.SubredditDetail.createRoute(subredditName))
                    }
                )
            }
            
            composable(Screen.Search.route) {
                SearchScreen(
                    onPostClick = { subreddit, postId ->
                        navController.navigate(DetailScreen.PostDetail.createRoute(subreddit, postId))
                    },
                    onSubredditClick = { subredditName ->
                        navController.navigate(DetailScreen.SubredditDetail.createRoute(subredditName))
                    },
                    onUserClick = { username ->
                        navController.navigate(DetailScreen.UserProfile.createRoute(username))
                    }
                )
            }
            
            composable(Screen.Inbox.route) {
                InboxScreen(
                    onPostClick = { subreddit, postId ->
                        navController.navigate(DetailScreen.PostDetail.createRoute(subreddit, postId))
                    }
                )
            }
            
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onPostClick = { subreddit, postId ->
                        navController.navigate(DetailScreen.PostDetail.createRoute(subreddit, postId))
                    },
                    onSubredditClick = { subredditName ->
                        navController.navigate(DetailScreen.SubredditDetail.createRoute(subredditName))
                    }
                )
            }
            
            composable(
                route = DetailScreen.SubredditDetail.route,
                arguments = listOf(navArgument("subredditName") { type = NavType.StringType })
            ) { backStackEntry ->
                val subredditName = backStackEntry.arguments?.getString("subredditName") ?: ""
                SubredditScreen(
                    subredditName = subredditName,
                    onBackClick = { navController.popBackStack() },
                    onPostClick = { subreddit, postId ->
                        navController.navigate(DetailScreen.PostDetail.createRoute(subreddit, postId))
                    },
                    onUserClick = { username ->
                        navController.navigate(DetailScreen.UserProfile.createRoute(username))
                    }
                )
            }
            
            composable(
                route = DetailScreen.PostDetail.route,
                arguments = listOf(
                    navArgument("subreddit") { type = NavType.StringType },
                    navArgument("postId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val subreddit = backStackEntry.arguments?.getString("subreddit") ?: ""
                val postId = backStackEntry.arguments?.getString("postId") ?: ""
                PostDetailScreen(
                    subreddit = subreddit,
                    postId = postId,
                    onBackClick = { navController.popBackStack() },
                    onSubredditClick = { subredditName ->
                        navController.navigate(DetailScreen.SubredditDetail.createRoute(subredditName))
                    },
                    onUserClick = { username ->
                        navController.navigate(DetailScreen.UserProfile.createRoute(username))
                    },
                    onLinkClick = { url ->
                        navController.navigate(DetailScreen.WebBrowser.createRoute(url))
                    }
                )
            }
            
            composable(
                route = DetailScreen.UserProfile.route,
                arguments = listOf(navArgument("username") { type = NavType.StringType })
            ) { backStackEntry ->
                val username = backStackEntry.arguments?.getString("username") ?: ""
                ProfileScreen(
                    username = username,
                    onBackClick = { navController.popBackStack() },
                    onPostClick = { subreddit, postId ->
                        navController.navigate(DetailScreen.PostDetail.createRoute(subreddit, postId))
                    },
                    onSubredditClick = { subredditName ->
                        navController.navigate(DetailScreen.SubredditDetail.createRoute(subredditName))
                    }
                )
            }

            composable(
                route = DetailScreen.WebBrowser.route,
                arguments = listOf(navArgument("url") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
                val url = java.net.URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString())
                WebBrowserScreen(
                    url = url,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
