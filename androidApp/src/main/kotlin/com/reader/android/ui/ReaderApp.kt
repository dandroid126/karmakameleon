package com.reader.android.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.reader.android.data.PendingNotificationAction
import com.reader.android.navigation.NavigationHandler
import com.reader.android.ui.components.FullScreenImageViewer
import com.reader.android.ui.components.FullScreenVideoScreen
import com.reader.android.ui.components.WebBrowserScreen
import com.reader.android.ui.components.YouTubeVideoScreen
import com.reader.android.ui.menu.GlobalMenuManager
import com.reader.shared.data.repository.UserRepository
import com.reader.shared.util.extractYouTubeVideoId
import com.reader.shared.util.isImageUrl
import com.reader.shared.util.isVideoUrl
import com.reader.shared.util.isYouTubeUrl
import com.reader.android.ui.feed.FeedScreen
import com.reader.android.ui.inbox.InboxScreen
import com.reader.android.ui.post.PostDetailScreen
import com.reader.android.ui.profile.ProfileScreen
import com.reader.android.ui.search.SearchScreen
import com.reader.android.ui.settings.SettingsScreen
import com.reader.android.ui.subreddit.SubredditListScreen
import com.reader.android.ui.subreddit.SubredditScreen
import com.reader.shared.domain.model.InboxFilter
import org.koin.compose.koinInject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Feed : Screen("feed", "Feed", Icons.Filled.Home, Icons.Outlined.Home)
    data object Subreddits : Screen("subreddits", "Subreddits", Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List)
    data object Search : Screen("search", "Search", Icons.Filled.Search, Icons.Outlined.Search)
}

sealed class DetailScreen(val route: String) {
    data object SubredditDetail : DetailScreen("subreddit/{subredditName}") {
        fun createRoute(subredditName: String) = "subreddit/$subredditName"
    }
    data object PostDetail : DetailScreen("post/{subreddit}/{postId}?commentId={commentId}&context={context}") {
        fun createRoute(subreddit: String, postId: String, commentId: String? = null, context: Int? = null): String {
            val base = "post/$subreddit/$postId"
            if (commentId == null) {
                return base
            }
            val comment = "$base?commentId=$commentId"
            return if (context != null) "$comment&context=$context" else comment
        }
    }
    data object UserProfile : DetailScreen("user/{username}") {
        fun createRoute(username: String) = "user/$username"
    }
    data object OwnProfile : DetailScreen("profile")
    data object Inbox : DetailScreen("inbox?filter={filter}") {
        fun createRoute(filter: InboxFilter? = null) =
            if (filter != null) "inbox?filter=${filter.name}" else "inbox"
    }
    data object WebBrowser : DetailScreen("web/{url}") {
        fun createRoute(url: String) = "web/${URLEncoder.encode(url, StandardCharsets.UTF_8.toString())}"
    }
    data object Settings : DetailScreen("settings")
    data object ImageViewer : DetailScreen("image/{url}") {
        fun createRoute(url: String) = "image/${URLEncoder.encode(url, StandardCharsets.UTF_8.toString())}"
    }
    data object VideoViewer : DetailScreen("video/{url}") {
        fun createRoute(url: String) = "video/${URLEncoder.encode(url, StandardCharsets.UTF_8.toString())}"
    }
    data object YouTubeViewer : DetailScreen("youtube/{videoId}") {
        fun createRoute(videoId: String) = "youtube/$videoId"
    }
}

val bottomNavItems = listOf(
    Screen.Feed,
    Screen.Subreddits,
    Screen.Search
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = navBackStackEntry?.destination?.route
    val navigationHandler = koinInject<NavigationHandler>()
    val globalMenuManager = koinInject<GlobalMenuManager>()
    val userRepository = koinInject<UserRepository>()
    val currentAccount by userRepository.currentAccount.collectAsState()

    LaunchedEffect(Unit) {
        userRepository.loadCurrentUser()
    }

    LaunchedEffect(currentAccount) {
        val onProfileRoute = currentRoute?.startsWith("profile") == true ||
            currentRoute?.startsWith("user/") == true
        if (onProfileRoute) {
            navController.navigate(Screen.Feed.route) {
                popUpTo(Screen.Feed.route) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(navController) {
        PendingNotificationAction.openInboxUnread.collect { shouldOpen ->
            if (shouldOpen) {
                PendingNotificationAction.consumeOpenInboxUnread()
                navController.navigate(DetailScreen.Inbox.createRoute(InboxFilter.UNREAD)) {
                    launchSingleTop = true
                }
            }
        }
    }

    DisposableEffect(navController) {
        globalMenuManager.onNavigateToProfile = { username ->
            navController.navigate(DetailScreen.UserProfile.createRoute(username))
        }
        globalMenuManager.onNavigateToOwnProfile = {
            navController.navigate(DetailScreen.OwnProfile.route)
        }
        globalMenuManager.onNavigateToInbox = {
            navController.navigate(DetailScreen.Inbox.createRoute())
        }
        globalMenuManager.onNavigateToSettings = {
            navController.navigate(DetailScreen.Settings.route)
        }
        navigationHandler.onSubredditClick = { name ->
            navController.navigate(DetailScreen.SubredditDetail.createRoute(name))
        }
        navigationHandler.onUserClick = { username ->
            navController.navigate(DetailScreen.UserProfile.createRoute(username))
        }
        navigationHandler.onExternalLinkClick = { url ->
            navController.navigate(DetailScreen.WebBrowser.createRoute(url))
        }
        navigationHandler.onPostClick = { subreddit, postId ->
            navController.navigate(DetailScreen.PostDetail.createRoute(subreddit, postId))
        }
        navigationHandler.onCommentClick = { subreddit, postId, commentId, context ->
            navController.navigate(DetailScreen.PostDetail.createRoute(subreddit, postId, commentId, context))
        }
        navigationHandler.onImageLinkClick = { url ->
            navController.navigate(DetailScreen.ImageViewer.createRoute(url))
        }
        navigationHandler.onVideoLinkClick = { url ->
            navController.navigate(DetailScreen.VideoViewer.createRoute(url))
        }
        navigationHandler.onYouTubeLinkClick = { url ->
            val videoId = extractYouTubeVideoId(url)
            if (videoId != null) {
                navController.navigate(DetailScreen.YouTubeViewer.createRoute(videoId))
            } else {
                navController.navigate(DetailScreen.WebBrowser.createRoute(url))
            }
        }
        onDispose {
            globalMenuManager.onNavigateToProfile = {}
            globalMenuManager.onNavigateToOwnProfile = {}
            globalMenuManager.onNavigateToInbox = {}
            globalMenuManager.onNavigateToSettings = {}
            navigationHandler.onSubredditClick = {}
            navigationHandler.onUserClick = {}
            navigationHandler.onExternalLinkClick = {}
            navigationHandler.onPostClick = { _, _ -> }
            navigationHandler.onCommentClick = { _, _, _, _ -> }
            navigationHandler.onImageLinkClick = {}
            navigationHandler.onVideoLinkClick = {}
            navigationHandler.onYouTubeLinkClick = {}
        }
    }
    
    val showBottomBar = bottomNavItems.any { screen ->
        currentDestination?.hierarchy?.any { it.route?.substringBefore('?') == screen.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route?.substringBefore('?') == screen.route } == true
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
                    currentRoute = currentRoute,
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
                        when {
                            isImageUrl(url) -> navController.navigate(DetailScreen.ImageViewer.createRoute(url))
                            isVideoUrl(url) -> navController.navigate(DetailScreen.VideoViewer.createRoute(url))
                            isYouTubeUrl(url) -> extractYouTubeVideoId(url)?.let { navController.navigate(DetailScreen.YouTubeViewer.createRoute(it)) } ?: navController.navigate(DetailScreen.WebBrowser.createRoute(url))
                            else -> navController.navigate(DetailScreen.WebBrowser.createRoute(url))
                        }
                    }
                )
            }
            // NOTE: FeedScreen/SearchScreen/SubredditScreen/PostCard still use explicit callbacks until PostCard is migrated
            
            composable(Screen.Subreddits.route) {
                SubredditListScreen(
                    currentRoute = currentRoute,
                    onSubredditClick = { subredditName ->
                        navController.navigate(DetailScreen.SubredditDetail.createRoute(subredditName))
                    }
                )
            }
            
            composable(Screen.Search.route) {
                SearchScreen(
                    currentRoute = currentRoute,
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
                        when {
                            isImageUrl(url) -> navController.navigate(DetailScreen.ImageViewer.createRoute(url))
                            isVideoUrl(url) -> navController.navigate(DetailScreen.VideoViewer.createRoute(url))
                            isYouTubeUrl(url) -> extractYouTubeVideoId(url)?.let { navController.navigate(DetailScreen.YouTubeViewer.createRoute(it)) } ?: navController.navigate(DetailScreen.WebBrowser.createRoute(url))
                            else -> navController.navigate(DetailScreen.WebBrowser.createRoute(url))
                        }
                    }
                )
            }
            
            composable(
                route = DetailScreen.Inbox.route,
                arguments = listOf(navArgument("filter") { type = NavType.StringType; nullable = true; defaultValue = null }),
                enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } },
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } }
            ) { backStackEntry ->
                val filterName = backStackEntry.arguments?.getString("filter")
                val initialFilter = filterName?.let { runCatching { InboxFilter.valueOf(it) }.getOrNull() }
                InboxScreen(
                    initialFilter = initialFilter,
                    currentRoute = currentRoute,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = DetailScreen.OwnProfile.route,
                enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } },
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } }
            ) {
                val ownUsername = currentAccount?.name
                ProfileScreen(
                    username = ownUsername,
                    currentRoute = currentRoute,
                    onBackClick = { navController.popBackStack() },
                    onPostClick = { subreddit, postId ->
                        navController.navigate(DetailScreen.PostDetail.createRoute(subreddit, postId))
                    },
                    onCommentClick = { subreddit, postId, commentId ->
                        navController.navigate(DetailScreen.PostDetail.createRoute(subreddit, postId, commentId))
                    },
                    onSubredditClick = { subredditName ->
                        navController.navigate(DetailScreen.SubredditDetail.createRoute(subredditName))
                    },
                    onLinkClick = { url ->
                        when {
                            isImageUrl(url) -> navController.navigate(DetailScreen.ImageViewer.createRoute(url))
                            isVideoUrl(url) -> navController.navigate(DetailScreen.VideoViewer.createRoute(url))
                            isYouTubeUrl(url) -> extractYouTubeVideoId(url)?.let { navController.navigate(DetailScreen.YouTubeViewer.createRoute(it)) } ?: navController.navigate(DetailScreen.WebBrowser.createRoute(url))
                            else -> navController.navigate(DetailScreen.WebBrowser.createRoute(url))
                        }
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
                    currentRoute = currentRoute,
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
                    currentRoute = currentRoute,
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
                        when {
                            isImageUrl(url) -> navController.navigate(DetailScreen.ImageViewer.createRoute(url))
                            isVideoUrl(url) -> navController.navigate(DetailScreen.VideoViewer.createRoute(url))
                            isYouTubeUrl(url) -> extractYouTubeVideoId(url)?.let { navController.navigate(DetailScreen.YouTubeViewer.createRoute(it)) } ?: navController.navigate(DetailScreen.WebBrowser.createRoute(url))
                            else -> navController.navigate(DetailScreen.WebBrowser.createRoute(url))
                        }
                    }
                )
            }
            
            composable(
                route = DetailScreen.PostDetail.route,
                arguments = listOf(
                    navArgument("subreddit") { type = NavType.StringType },
                    navArgument("postId") { type = NavType.StringType },
                    navArgument("commentId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("context") { type = NavType.IntType; defaultValue = 0 }
                ),
                enterTransition = { slideInHorizontally(animationSpec = tween(300)) { it } },
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { it } }
            ) { backStackEntry ->
                val subreddit = backStackEntry.arguments?.getString("subreddit") ?: ""
                val postId = backStackEntry.arguments?.getString("postId") ?: ""
                val commentId = backStackEntry.arguments?.getString("commentId")
                val commentContext = backStackEntry.arguments?.getInt("context")
                PostDetailScreen(
                    subreddit = subreddit,
                    postId = postId,
                    commentId = commentId,
                    commentContext = commentContext,
                    currentRoute = currentRoute,
                    onBackClick = { navController.popBackStack() },
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
                    currentRoute = currentRoute,
                    onBackClick = { navController.popBackStack() },
                    onPostClick = { subreddit, postId ->
                        navController.navigate(DetailScreen.PostDetail.createRoute(subreddit, postId))
                    },
                    onCommentClick = { subreddit, postId, commentId ->
                        navController.navigate(DetailScreen.PostDetail.createRoute(subreddit, postId, commentId))
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

            composable(
                route = DetailScreen.ImageViewer.route,
                arguments = listOf(navArgument("url") { type = NavType.StringType }),
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                popExitTransition = { fadeOut(animationSpec = tween(300)) }
            ) { backStackEntry ->
                val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
                val url = java.net.URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString())
                FullScreenImageViewer(
                    imageUrls = listOf(url),
                    onDismiss = { navController.popBackStack() }
                )
            }

            composable(
                route = DetailScreen.VideoViewer.route,
                arguments = listOf(navArgument("url") { type = NavType.StringType }),
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                popExitTransition = { fadeOut(animationSpec = tween(300)) }
            ) { backStackEntry ->
                val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
                val url = java.net.URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString())
                FullScreenVideoScreen(
                    videoUrl = url,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = DetailScreen.YouTubeViewer.route,
                arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                popExitTransition = { fadeOut(animationSpec = tween(300)) }
            ) { backStackEntry ->
                val videoId = backStackEntry.arguments?.getString("videoId") ?: ""
                YouTubeVideoScreen(
                    videoId = videoId,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
