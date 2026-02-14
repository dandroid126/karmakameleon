package com.reader.android.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.reader.android.ui.components.RedditLink
import com.reader.android.ui.components.WebBrowserScreen
import com.reader.android.ui.components.parseRedditLink
import com.reader.android.ui.feed.FeedScreen
import com.reader.android.ui.inbox.InboxScreen
import com.reader.android.ui.post.PostDetailScreen
import com.reader.android.ui.profile.ProfileScreen
import com.reader.android.ui.search.SearchScreen
import com.reader.android.ui.settings.SettingsScreen
import com.reader.android.ui.subreddit.SubredditListScreen
import com.reader.android.ui.subreddit.SubredditScreen
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
    data object PostDetail : DetailScreen("post/{subreddit}/{postId}?commentId={commentId}") {
        fun createRoute(subreddit: String, postId: String, commentId: String? = null): String {
            val base = "post/$subreddit/$postId"
            return if (commentId != null) "$base?commentId=$commentId" else base
        }
    }
    data object UserProfile : DetailScreen("user/{username}") {
        fun createRoute(username: String) = "user/$username"
    }
    data object WebBrowser : DetailScreen("web/{url}") {
        fun createRoute(url: String) = "web/${URLEncoder.encode(url, StandardCharsets.UTF_8.toString())}"
    }
    data object Settings : DetailScreen("settings")
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
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
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
                    onCommentClick = { subreddit, postId, commentId ->
                        navController.navigate(DetailScreen.PostDetail.createRoute(subreddit, postId, commentId))
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
                    },
                    onLinkClick = { url ->
                        navController.navigate(DetailScreen.WebBrowser.createRoute(url))
                    },
                    onSettingsClick = {
                        navController.navigate(DetailScreen.Settings.route)
                    }
                )
            }

            composable(
                route = DetailScreen.Settings.route,
                enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } },
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } }
            ) {
                SettingsScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
            
            composable(
                route = DetailScreen.SubredditDetail.route,
                arguments = listOf(navArgument("subredditName") { type = NavType.StringType }),
                enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } },
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } }
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
                    },
                    onSubredditClick = { name ->
                        navController.navigate(DetailScreen.SubredditDetail.createRoute(name))
                    },
                    onLinkClick = { url ->
                        navController.navigate(DetailScreen.WebBrowser.createRoute(url))
                    }
                )
            }
            
            composable(
                route = DetailScreen.PostDetail.route,
                arguments = listOf(
                    navArgument("subreddit") { type = NavType.StringType },
                    navArgument("postId") { type = NavType.StringType },
                    navArgument("commentId") { type = NavType.StringType; nullable = true; defaultValue = null }
                ),
                enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } },
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } }
            ) { backStackEntry ->
                val subreddit = backStackEntry.arguments?.getString("subreddit") ?: ""
                val postId = backStackEntry.arguments?.getString("postId") ?: ""
                val commentId = backStackEntry.arguments?.getString("commentId")
                PostDetailScreen(
                    subreddit = subreddit,
                    postId = postId,
                    commentId = commentId,
                    onBackClick = { navController.popBackStack() },
                    onSubredditClick = { subredditName ->
                        navController.navigate(DetailScreen.SubredditDetail.createRoute(subredditName))
                    },
                    onUserClick = { username ->
                        navController.navigate(DetailScreen.UserProfile.createRoute(username))
                    },
                    onLinkClick = { url ->
                        when (val link = parseRedditLink(url)) {
                            is RedditLink.Subreddit -> navController.navigate(DetailScreen.SubredditDetail.createRoute(link.name))
                            is RedditLink.User -> navController.navigate(DetailScreen.UserProfile.createRoute(link.name))
                            is RedditLink.Post -> navController.navigate(DetailScreen.PostDetail.createRoute(link.subreddit, link.postId))
                            is RedditLink.Comment -> navController.navigate(DetailScreen.PostDetail.createRoute(link.subreddit, link.postId, link.commentId))
                            is RedditLink.External -> navController.navigate(DetailScreen.WebBrowser.createRoute(url))
                        }
                    },
                    onGoToCommentNav = { targetCommentId ->
                        navController.navigate(DetailScreen.PostDetail.createRoute(subreddit, postId, targetCommentId))
                    }
                )
            }
            
            composable(
                route = DetailScreen.UserProfile.route,
                arguments = listOf(navArgument("username") { type = NavType.StringType }),
                enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } },
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } }
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
                arguments = listOf(navArgument("url") { type = NavType.StringType }),
                enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } },
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } }
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
