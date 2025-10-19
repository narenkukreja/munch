package com.munch.reddit.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.munch.reddit.domain.model.RedditPost
import com.munch.reddit.domain.model.RedditPostMedia
import com.munch.reddit.feature.feed.PostBackgroundColor
import com.munch.reddit.feature.feed.SubredditColor
import com.munch.reddit.feature.feed.TitleColor
import com.munch.reddit.ui.theme.MunchForRedditTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectThemeScreen(
    onThemeSelected: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val backgroundColor = if (onBack != null) {
        PostBackgroundColor
    } else {
        MaterialTheme.colorScheme.background
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            if (onBack != null) {
                LargeTopAppBar(
                    title = {
                        Text(
                            text = "Themes",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TitleColor
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TitleColor
                            )
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = backgroundColor,
                        scrolledContainerColor = backgroundColor,
                        titleContentColor = TitleColor,
                        navigationIconContentColor = TitleColor,
                        actionIconContentColor = TitleColor
                    )
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(
                modifier = Modifier.height(
                    if (onBack == null) 30.dp else 16.dp
                )
            )

            Text(
                text = "Select Your Theme!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TitleColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Feed Preview
            Text(
                text = "Preview",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TitleColor
            )
            Spacer(modifier = Modifier.height(16.dp))
            OnboardingPostPreview()

            Spacer(modifier = Modifier.height(32.dp))

            // Theme Selection Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = PostBackgroundColor,
                border = BorderStroke(2.dp, SubredditColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(SubredditColor)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Wormi (default)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TitleColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Continue Button
            Button(
                onClick = onThemeSelected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SubredditColor
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Continue",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private val onboardingSamplePost = RedditPost(
    id = "onboarding_sample",
    title = "Posts title",
    author = "wormi_reddit",
    subreddit = "r/john_doe",
    selfText = "",
    thumbnailUrl = "https://placehold.co/300x300/png",
    url = "https://placehold.co/300x300/png",
    domain = "imgur.com",
    permalink = "/r/androiddev/comments/sample",
    commentCount = 342,
    score = 2500,
    createdUtc = (System.currentTimeMillis() / 1000) - 3600,
    media = RedditPostMedia.Image(
        url = "https://placehold.co/300x300/png",
        width = 300,
        height = 300
    ),
    isStickied = false,
    isNsfw = false
)

@Composable
private fun OnboardingPostPreview() {
    com.munch.reddit.feature.feed.RedditPostItem(
        post = onboardingSamplePost,
        selectedSubreddit = "all",
        onSubredditTapped = {},
        onPostSelected = {},
        onImageClick = {},
        modifier = Modifier.fillMaxWidth()
    )
}

@Preview(showBackground = true)
@Composable
private fun SelectThemeScreenPreview() {
    MunchForRedditTheme {
        SelectThemeScreen(onThemeSelected = {})
    }
}
