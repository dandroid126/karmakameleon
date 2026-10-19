package com.karmakameleon.android.ui

import androidx.compose.animation.AnimatedContentScope
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.karmakameleon.android.data.PendingNotificationAction
import com.karmakameleon.android.navigation.NavigationHandler
import com.karmakameleon.android.ui.components.FullScreenImageViewer
import com.karmakameleon.android.ui.components.FullScreenVideoScreen
import com.karmakameleon.android.ui.components.WebBrowserScreen
import com.karmakameleon.android.ui.components.YouTubeVideoScreen
import com.karmakameleon.android.ui.feed.FeedScreen
import com.karmakameleon.android.ui.inbox.InboxScreen
import com.karmakameleon.android.ui.menu.GlobalMenuManager
import com.karmakameleon.android.ui.post.PostDetailScreen
import com.karmakameleon.android.ui.profile.ProfileScreen
import com.karmakameleon.android.ui.search.SearchScreen
import com.karmakameleon.android.ui.settings.SettingsScreen
import com.karmakameleon.android.ui.subreddit.SubredditListScreen
import com.karmakameleon.android.ui.subreddit.SubredditScreen
import com.karmakameleon.shared.data.api.RedditApi
import com.karmakameleon.shared.data.repository.UserRepository
import com.karmakameleon.shared.domain.model.InboxFilter
import com.karmakameleon.shared.util.extractYouTubeVideoId
import com.karmakameleon.shared.util.isImageUrl
import com.karmakameleon.shared.util.isVideoUrl
import com.karmakameleon.shared.util.isYouTubeUrl
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    data object Feed : Screen("feed", "Feed", Icons.Filled.Home, Icons.Outlined.Home)

    data object Subreddits : Screen("subreddits", "Subreddits", Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List)

    data object Search : Screen("search", "Search", Icons.Filled.Search, Icons.Outlined.Search)
}

sealed class DetailScreen(
    val route: String,
) {
    data object SubredditDetail : DetailScreen("subreddit/{subredditName}") {
        fun createRoute(subredditName: String) = "subreddit/$subredditName"
    }

    data object PostDetail : DetailScreen("post/{subreddit}/{postId}?commentId={commentId}&context={context}") {
        fun createRoute(
            subreddit: String,
            postId: String,
            commentId: String? = null,
            context: Int? = null,
        ): String {
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
        fun createRoute(filter: InboxFilter? = null) = if (filter != null) "inbox?filter=${filter.name}" else "inbox"
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

val bottomNavItems =
    listOf(
        Screen.Feed,
        Screen.Subreddits,
        Screen.Search,
    )

@Composable
fun KarmaKameleonApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val userRepository = koinInject<UserRepository>()
    val currentAccount by userRepository.currentAccount.collectAsState()

    AppStartupEffects(
        navController = navController,
        isLoggedIn = currentAccount != null,
        currentRoute = currentRoute,
        loadCurrentUser = { userRepository.loadCurrentUser() },
    )
    BindNavigation(navController)

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (bottomNavItems.any { currentDestination.isOnTab(it) }) {
                AppBottomBar(navController = navController, currentDestination = currentDestination)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Feed.route,
            modifier =
                Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
        ) {
            mainTabs(navController, currentRoute)
            profileAndSettingsScreens(navController, currentRoute, ownUsername = currentAccount?.name)
            contentScreens(navController, currentRoute)
            mediaViewers(navController)
        }
    }
}

// Loads the logged-in user, leaves profile screens on logout, and opens unread messages when a notification asks.
@Composable
private fun AppStartupEffects(
    navController: NavController,
    isLoggedIn: Boolean,
    currentRoute: String?,
    loadCurrentUser: suspend () -> Unit,
) {
    val currentLoadCurrentUser by rememberUpdatedState(loadCurrentUser)
    LaunchedEffect(Unit) {
        currentLoadCurrentUser()
    }

    SideEffect(isLoggedIn) {
        val onProfileRoute = currentRoute?.startsWith("profile") == true || currentRoute?.startsWith("user/") == true
        if (onProfileRoute && !isLoggedIn) {
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
}

// Points the app-wide navigation handler and overflow menu at this NavController while it's shown.
@Composable
private fun BindNavigation(
    navController: NavController,
    navigationHandler: NavigationHandler = koinInject(),
    globalMenuManager: GlobalMenuManager = koinInject(),
    redditApi: RedditApi = koinInject(),
) {
    val coroutineScope = rememberCoroutineScope()
    DisposableEffect(navController) {
        globalMenuManager.bindTo(navController)
        navigationHandler.bindTo(navController) { subreddit, shareId ->
            coroutineScope.launch {
                redditApi.resolveShareLink(subreddit, shareId)?.let { (resolvedSubreddit, postId) ->
                    navController.openPost(resolvedSubreddit, postId)
                }
            }
        }
        onDispose {
            globalMenuManager.unbind()
            navigationHandler.unbind()
        }
    }
}

private fun GlobalMenuManager.bindTo(navController: NavController) {
    onNavigateToProfile = navController::openUser
    onNavigateToOwnProfile = { navController.navigate(DetailScreen.OwnProfile.route) }
    onNavigateToInbox = { navController.navigate(DetailScreen.Inbox.createRoute()) }
    onNavigateToSettings = { navController.navigate(DetailScreen.Settings.route) }
}

private fun GlobalMenuManager.unbind() {
    onNavigateToProfile = {}
    onNavigateToOwnProfile = {}
    onNavigateToInbox = {}
    onNavigateToSettings = {}
}

// Share links are resolved by Reddit, so opening one is up to the caller.
private fun NavigationHandler.bindTo(
    navController: NavController,
    openShareLink: (subreddit: String, shareId: String) -> Unit,
) {
    onSubredditClick = navController::openSubreddit
    onUserClick = navController::openUser
    onExternalLinkClick = navController::openInBrowser
    onPostClick = { subreddit, postId -> navController.openPost(subreddit, postId) }
    onCommentClick = { subreddit, postId, commentId, context ->
        navController.navigate(DetailScreen.PostDetail.createRoute(subreddit, postId, commentId, context))
    }
    onImageLinkClick = { url -> navController.navigate(DetailScreen.ImageViewer.createRoute(url)) }
    onVideoLinkClick = { url -> navController.navigate(DetailScreen.VideoViewer.createRoute(url)) }
    onYouTubeLinkClick = navController::openYouTube
    onShareLinkClick = openShareLink
}

private fun NavigationHandler.unbind() {
    onSubredditClick = {}
    onUserClick = {}
    onExternalLinkClick = {}
    onPostClick = { _, _ -> }
    onCommentClick = { _, _, _, _ -> }
    onImageLinkClick = {}
    onVideoLinkClick = {}
    onYouTubeLinkClick = {}
    onShareLinkClick = { _, _ -> }
}

private fun NavDestination?.isOnTab(screen: Screen): Boolean =
    this?.hierarchy?.any { it.route?.substringBefore('?') == screen.route } == true

@Composable
private fun AppBottomBar(
    navController: NavController,
    currentDestination: NavDestination?,
) {
    NavigationBar {
        bottomNavItems.forEach { screen ->
            val selected = currentDestination.isOnTab(screen)
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                        contentDescription = screen.title,
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
                },
            )
        }
    }
}

private fun NavController.openPost(
    subreddit: String,
    postId: String,
    commentId: String? = null,
) {
    navigate(DetailScreen.PostDetail.createRoute(subreddit, postId, commentId))
}

private fun NavController.openSubreddit(name: String) = navigate(DetailScreen.SubredditDetail.createRoute(name))

private fun NavController.openUser(username: String) = navigate(DetailScreen.UserProfile.createRoute(username))

private fun NavController.openInBrowser(url: String) = navigate(DetailScreen.WebBrowser.createRoute(url))

// YouTube links the app can't read a video ID from open in the browser.
private fun NavController.openYouTube(url: String) {
    val videoId = extractYouTubeVideoId(url)
    if (videoId != null) navigate(DetailScreen.YouTubeViewer.createRoute(videoId)) else openInBrowser(url)
}

// Images, videos and YouTube videos open in their viewers; anything else in the browser.
private fun NavController.openLink(url: String) {
    when {
        isImageUrl(url) -> navigate(DetailScreen.ImageViewer.createRoute(url))
        isVideoUrl(url) -> navigate(DetailScreen.VideoViewer.createRoute(url))
        isYouTubeUrl(url) -> openYouTube(url)
        else -> openInBrowser(url)
    }
}

private const val TRANSITION_MS = 300

// A screen that slides in from the right.
private fun NavGraphBuilder.detailScreen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        arguments = arguments,
        enterTransition = { slideInHorizontally(animationSpec = tween(TRANSITION_MS)) { it } },
        exitTransition = { fadeOut(animationSpec = tween(TRANSITION_MS)) },
        popEnterTransition = { fadeIn(animationSpec = tween(TRANSITION_MS)) },
        popExitTransition = { slideOutHorizontally(animationSpec = tween(TRANSITION_MS)) { it } },
        content = content,
    )
}

// A full-screen media viewer for the URL in its route, which fades in and out.
private fun NavGraphBuilder.mediaViewer(
    route: String,
    argument: String,
    content: @Composable (String) -> Unit,
) {
    composable(
        route = route,
        arguments = listOf(navArgument(argument) { type = NavType.StringType }),
        enterTransition = { fadeIn(animationSpec = tween(TRANSITION_MS)) },
        exitTransition = { fadeOut(animationSpec = tween(TRANSITION_MS)) },
        popEnterTransition = { fadeIn(animationSpec = tween(TRANSITION_MS)) },
        popExitTransition = { fadeOut(animationSpec = tween(TRANSITION_MS)) },
    ) { backStackEntry -> content(backStackEntry.stringArgument(argument)) }
}

private fun NavBackStackEntry.stringArgument(name: String): String = arguments?.getString(name).orEmpty()

private fun NavBackStackEntry.urlArgument(name: String): String = decodeUrl(stringArgument(name))

private fun NavGraphBuilder.mainTabs(
    navController: NavController,
    currentRoute: String?,
) {
    composable(Screen.Feed.route) {
        FeedScreen(
            currentRoute = currentRoute,
            onPostClick = { subreddit, postId -> navController.openPost(subreddit, postId) },
            onSubredditClick = navController::openSubreddit,
            onUserClick = navController::openUser,
            onLinkClick = navController::openLink,
        )
    }
    composable(Screen.Subreddits.route) {
        SubredditListScreen(currentRoute = currentRoute, onSubredditClick = navController::openSubreddit)
    }
    composable(Screen.Search.route) {
        SearchScreen(
            currentRoute = currentRoute,
            onPostClick = { subreddit, postId -> navController.openPost(subreddit, postId) },
            onCommentClick = navController::openPost,
            onSubredditClick = navController::openSubreddit,
            onUserClick = navController::openUser,
            onLinkClick = navController::openLink,
        )
    }
}

private fun NavGraphBuilder.profileAndSettingsScreens(
    navController: NavController,
    currentRoute: String?,
    ownUsername: String?,
) {
    detailScreen(
        route = DetailScreen.Inbox.route,
        arguments =
            listOf(
                navArgument("filter") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        val filterName = backStackEntry.arguments?.getString("filter")
        InboxScreen(
            initialFilter = filterName?.let { runCatching { InboxFilter.valueOf(it) }.getOrNull() },
            currentRoute = currentRoute,
            onBackClick = { navController.popBackStack() },
        )
    }
    detailScreen(DetailScreen.OwnProfile.route) {
        ProfileScreen(
            username = ownUsername,
            currentRoute = currentRoute,
            onBackClick = { navController.popBackStack() },
            onPostClick = { subreddit, postId -> navController.openPost(subreddit, postId) },
            onCommentClick = navController::openPost,
            onSubredditClick = navController::openSubreddit,
            onLinkClick = navController::openLink,
        )
    }
    detailScreen(DetailScreen.UserProfile.route, listOf(navArgument("username") { type = NavType.StringType })) { backStackEntry ->
        ProfileScreen(
            username = backStackEntry.stringArgument("username"),
            currentRoute = currentRoute,
            onBackClick = { navController.popBackStack() },
            onPostClick = { subreddit, postId -> navController.openPost(subreddit, postId) },
            onCommentClick = navController::openPost,
            onSubredditClick = navController::openSubreddit,
        )
    }
    detailScreen(DetailScreen.Settings.route) {
        SettingsScreen(currentRoute = currentRoute, onBackClick = { navController.popBackStack() })
    }
}

private fun NavGraphBuilder.contentScreens(
    navController: NavController,
    currentRoute: String?,
) {
    detailScreen(DetailScreen.SubredditDetail.route, listOf(navArgument("subredditName") { type = NavType.StringType })) { backStackEntry ->
        SubredditScreen(
            subredditName = backStackEntry.stringArgument("subredditName"),
            currentRoute = currentRoute,
            onBackClick = { navController.popBackStack() },
            onPostClick = { subreddit, postId -> navController.openPost(subreddit, postId) },
            onUserClick = navController::openUser,
            onSubredditClick = navController::openSubreddit,
            onLinkClick = navController::openLink,
        )
    }
    detailScreen(
        route = DetailScreen.PostDetail.route,
        arguments =
            listOf(
                navArgument("subreddit") { type = NavType.StringType },
                navArgument("postId") { type = NavType.StringType },
                navArgument("commentId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("context") {
                    type = NavType.IntType
                    defaultValue = 0
                },
            ),
    ) { backStackEntry ->
        val subreddit = backStackEntry.stringArgument("subreddit")
        val postId = backStackEntry.stringArgument("postId")
        PostDetailScreen(
            subreddit = subreddit,
            postId = postId,
            commentId = backStackEntry.arguments?.getString("commentId"),
            commentContext = backStackEntry.arguments?.getInt("context"),
            currentRoute = currentRoute,
            onBackClick = { navController.popBackStack() },
            onGoToCommentNav = { targetCommentId -> navController.openPost(subreddit, postId, targetCommentId) },
        )
    }
    detailScreen(DetailScreen.WebBrowser.route, listOf(navArgument("url") { type = NavType.StringType })) { backStackEntry ->
        WebBrowserScreen(url = backStackEntry.urlArgument("url"), onBackClick = { navController.popBackStack() })
    }
}

private fun NavGraphBuilder.mediaViewers(navController: NavController) {
    val goBack: () -> Unit = { navController.popBackStack() }
    mediaViewer(DetailScreen.ImageViewer.route, "url") { encodedUrl ->
        FullScreenImageViewer(imageUrls = listOf(decodeUrl(encodedUrl)), onDismiss = goBack)
    }
    mediaViewer(DetailScreen.VideoViewer.route, "url") { encodedUrl ->
        FullScreenVideoScreen(videoUrl = decodeUrl(encodedUrl), onBackClick = goBack)
    }
    mediaViewer(DetailScreen.YouTubeViewer.route, "videoId") { videoId ->
        YouTubeVideoScreen(videoId = videoId, onBackClick = goBack)
    }
}

private fun decodeUrl(encodedUrl: String): String = java.net.URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString())
