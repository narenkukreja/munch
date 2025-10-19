package com.munch.reddit.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.munch.reddit.feature.feed.FeedTheme
import com.munch.reddit.feature.feed.MetaInfoColor
import com.munch.reddit.feature.feed.PostBackgroundColor
import com.munch.reddit.feature.feed.SubredditColor
import com.munch.reddit.feature.feed.TitleColor
import com.munch.reddit.ui.theme.MunchForRedditTheme
import com.munch.reddit.theme.FeedThemePreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectThemeScreen(
    initialThemeId: String,
    onThemeSelected: (String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val backgroundColor = if (onBack != null) PostBackgroundColor else MaterialTheme.colorScheme.background
    var selectedThemeId by rememberSaveable { mutableStateOf(initialThemeId.lowercase()) }

    LaunchedEffect(initialThemeId) {
        selectedThemeId = initialThemeId.lowercase()
    }

    val selectedPreset = FeedThemePreset.fromId(selectedThemeId)

    Scaffold(
        containerColor = backgroundColor,
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
                text = "Theme Preview",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TitleColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            FeedTheme(selectedPreset) {

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OnboardingPostPreview()
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ThemeOptionButton(
                    label = "Wormi",
                    isSelected = selectedThemeId == FeedThemePreset.Wormi.id,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedThemeId = FeedThemePreset.Wormi.id }
                )
                ThemeOptionButton(
                    label = "Narwhal",
                    isSelected = selectedThemeId == FeedThemePreset.Narwhal.id,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedThemeId = FeedThemePreset.Narwhal.id }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { onThemeSelected(selectedThemeId) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Save",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ThemeOptionButton(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 6.dp)
        )
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
        SelectThemeScreen(
            initialThemeId = FeedThemePreset.Wormi.id,
            onThemeSelected = {}
        )
    }
}
