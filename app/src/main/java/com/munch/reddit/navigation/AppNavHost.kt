package com.munch.reddit.navigation

import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.munch.reddit.data.AppPreferences
import com.munch.reddit.feature.detail.PostDetailRoute
import com.munch.reddit.feature.feed.RedditFeedRoute
import com.munch.reddit.feature.feed.RedditFeedViewModel
import com.munch.reddit.feature.onboarding.SelectThemeScreen
import com.munch.reddit.feature.onboarding.WelcomeScreen
import com.munch.reddit.feature.search.SearchRoute
import com.munch.reddit.feature.settings.SettingsRoute
import com.munch.reddit.feature.shared.ImagePreviewRoute
import com.munch.reddit.feature.feed.FeedTheme
import com.munch.reddit.theme.FeedThemePreset

private const val WELCOME_ROUTE = "welcome"
private const val SELECT_THEME_ROUTE = "select_theme"
private const val FEED_ROUTE = "feed"
private const val DETAIL_ROUTE = "detail/{permalink}"
private const val IMAGE_PREVIEW_ROUTE = "image_preview/{imageUrl}"
private const val YOUTUBE_PLAYER_ROUTE = "youtube/{videoId}"
private const val VIDEO_FEED_ROUTE = "video_feed"
private const val SEARCH_ROUTE = "search"
private const val SETTINGS_ROUTE = "settings"
private const val SETTINGS_THEME_ROUTE = "settings_theme"

fun detailRoute(permalink: String): String = "detail/${Uri.encode(permalink)}"
fun imagePreviewRoute(imageUrl: String): String = "image_preview/${Uri.encode(imageUrl)}"
fun youtubePlayerRoute(videoId: String): String = "youtube/${Uri.encode(videoId)}"

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context) }
    val navController = rememberNavController()
    var feedThemeId by remember { mutableStateOf(appPreferences.selectedTheme) }
    val feedThemePreset = remember(feedThemeId) { FeedThemePreset.fromId(feedThemeId) }

    // Determine start destination based on onboarding completion
    val startDestination = if (appPreferences.hasCompletedOnboarding) {
        FEED_ROUTE
    } else {
        WELCOME_ROUTE
    }

    FeedTheme(feedThemePreset) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = modifier.background(Color.Black)
        ) {
            composable(WELCOME_ROUTE) {
                WelcomeScreen(
                    onStartClick = {
                        navController.navigate(SELECT_THEME_ROUTE) {
                            popUpTo(WELCOME_ROUTE) { inclusive = true }
                        }
                    }
                )
            }

            composable(SELECT_THEME_ROUTE) {
                SelectThemeScreen(
                    initialThemeId = feedThemeId,
                    onThemeSelected = { themeId ->
                        val normalized = themeId.lowercase()
                        appPreferences.selectedTheme = normalized
                        feedThemeId = normalized
                        appPreferences.hasCompletedOnboarding = true
                        navController.navigate(FEED_ROUTE) {
                            popUpTo(SELECT_THEME_ROUTE) { inclusive = true }
                        }
                    }
                )
            }
            composable(FEED_ROUTE) { backStackEntry ->
                RedditFeedRoute(
                    backStackEntry = backStackEntry,
                    onPostSelected = { post ->
                        navController.navigate(detailRoute(post.permalink))
                    },
                    onImageSelected = { imageUrl ->
                        navController.navigate(imagePreviewRoute(imageUrl))
                    },
                    onYouTubeSelected = { videoId ->
                        navController.navigate(youtubePlayerRoute(videoId))
                    },
                    onSearchClick = {
                        navController.navigate(SEARCH_ROUTE)
                    },
                    onSettingsClick = {
                        navController.navigate(SETTINGS_ROUTE)
                    },
                    onVideoFeedClick = {
                        navController.navigate(VIDEO_FEED_ROUTE)
                    }
                )
            }
            composable(
                route = DETAIL_ROUTE,
                arguments = listOf(navArgument("permalink") { type = NavType.StringType }),
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(animationSpec = tween(200))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(200))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(200))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeOut(animationSpec = tween(200))
                }
            ) { backStackEntry ->
                val permalink = backStackEntry.arguments?.getString("permalink")?.let(Uri::decode)
                if (permalink != null) {
                    PostDetailRoute(
                        navController = navController,
                        permalink = permalink
                    )
                } else {
                    navController.popBackStack()
                }
            }
            composable(
                route = IMAGE_PREVIEW_ROUTE,
                arguments = listOf(navArgument("imageUrl") { type = NavType.StringType }),
                enterTransition = {
                    fadeIn(animationSpec = tween(300))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(200))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(200))
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(300))
                }
            ) { backStackEntry ->
                val imageUrl = backStackEntry.arguments?.getString("imageUrl")?.let(Uri::decode)
                if (imageUrl != null) {
                    ImagePreviewRoute(navController = navController, imageUrl = imageUrl)
                } else {
                    navController.popBackStack()
                }
            }
            composable(
                route = YOUTUBE_PLAYER_ROUTE,
                arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(200)) },
                popEnterTransition = { fadeIn(animationSpec = tween(200)) },
                popExitTransition = { fadeOut(animationSpec = tween(300)) }
            ) { backStackEntry ->
                val videoId = backStackEntry.arguments?.getString("videoId")?.let(Uri::decode)
                if (videoId != null) {
                    com.munch.reddit.feature.shared.YouTubePlayerRoute(
                        navController = navController,
                        videoId = videoId
                    )
                } else {
                    navController.popBackStack()
                }
            }
            composable(
                route = VIDEO_FEED_ROUTE,
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(animationSpec = tween(200))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(200))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(200))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeOut(animationSpec = tween(200))
                }
            ) {
                val feedBackStackEntry = navController.getBackStackEntry(FEED_ROUTE)
                com.munch.reddit.feature.videofeed.VideoFeedRoute(
                    navController = navController,
                    feedBackStackEntry = feedBackStackEntry
                )
            }
            composable(
                route = SEARCH_ROUTE,
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(animationSpec = tween(200))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(200))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(200))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeOut(animationSpec = tween(200))
                }
            ) {
                SearchRoute(
                    onBackClick = { navController.popBackStack() },
                    onSearchAllPosts = { query ->
                        // Navigate back to feed with search query
                        navController.popBackStack()
                        runCatching {
                            navController.getBackStackEntry(FEED_ROUTE).savedStateHandle["searchQuery"] =
                                query
                        }
                    },
                    onViewSubreddit = { subreddit ->
                        navController.navigateToFeedSubreddit(subreddit)
                    }
                )
            }
            composable(
                route = SETTINGS_ROUTE,
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(animationSpec = tween(200))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(200))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(200))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeOut(animationSpec = tween(200))
                }
            ) {
                SettingsRoute(
                    onBack = { navController.popBackStack() },
                    onThemeClick = { navController.navigate(SETTINGS_THEME_ROUTE) }
                )
            }
            composable(
                route = SETTINGS_THEME_ROUTE,
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(animationSpec = tween(200))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(200))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(200))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeOut(animationSpec = tween(200))
                }
            ) {
                SelectThemeScreen(
                    initialThemeId = feedThemeId,
                    onThemeSelected = { themeId ->
                        val normalized = themeId.lowercase()
                        appPreferences.selectedTheme = normalized
                        feedThemeId = normalized
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

internal fun NavController.navigateToFeedSubreddit(rawSubreddit: String) {
    val target = rawSubreddit.removePrefix("r/").removePrefix("R/").trim().lowercase()
    if (target.isEmpty()) return

    runCatching {
        getBackStackEntry(FEED_ROUTE).savedStateHandle["requestedSubreddit"] = target
    }

    popBackStack(FEED_ROUTE, inclusive = false)
}
