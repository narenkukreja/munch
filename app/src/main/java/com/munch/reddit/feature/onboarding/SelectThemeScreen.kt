package com.munch.reddit.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.munch.reddit.theme.TextSizeDefaults
import com.munch.reddit.domain.model.RedditPost
import com.munch.reddit.domain.model.RedditPostMedia
import com.munch.reddit.feature.feed.FeedTheme
import com.munch.reddit.feature.feed.MetaInfoColor
import com.munch.reddit.feature.feed.PostBackgroundColor
import com.munch.reddit.feature.feed.SpacerBackgroundColor
import com.munch.reddit.feature.feed.CommentTextColor
import com.munch.reddit.feature.feed.SubredditColor
import com.munch.reddit.feature.feed.TitleColor
import com.munch.reddit.ui.theme.MunchForRedditTheme
import com.munch.reddit.ui.theme.MaterialSpacing
import com.munch.reddit.ui.theme.withCommentTextSize
import com.munch.reddit.theme.FeedThemePreset
import com.munch.reddit.theme.PostCardStyle
import com.munch.reddit.feature.shared.LinkifiedText
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowUpward
import kotlin.collections.buildList
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectThemeScreen(
    initialThemeId: String,
    initialPostCardStyleId: String,
    initialTextSize: Float,
    onSelectionSaved: (String, String, Float) -> Unit,
    onTextSizeChanged: (Float) -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    // Match feed/detail backgrounds so previews look accurate
    val backgroundColor = SpacerBackgroundColor
    var selectedThemeId by rememberSaveable { mutableStateOf(initialThemeId.lowercase()) }
    var selectedPostCardStyleId by rememberSaveable { mutableStateOf(initialPostCardStyleId.lowercase()) }
    var selectedTextSize by rememberSaveable { mutableStateOf(TextSizeDefaults.clamp(initialTextSize)) }

    LaunchedEffect(initialThemeId) {
        selectedThemeId = initialThemeId.lowercase()
    }

    LaunchedEffect(initialPostCardStyleId) {
        selectedPostCardStyleId = initialPostCardStyleId.lowercase()
    }

    LaunchedEffect(initialTextSize) {
        selectedTextSize = TextSizeDefaults.clamp(initialTextSize)
    }

    val selectedPreset = FeedThemePreset.fromId(selectedThemeId)
    val selectedPostCardStyle = PostCardStyle.fromId(selectedPostCardStyleId)

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

                // Transparent container so no extra surface color shows behind previews
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OnboardingPostPreview(postCardStyle = selectedPostCardStyle)
                        Text(
                            text = "Comment",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TitleColor
                        )
                        OnboardingCommentPreview(textSize = selectedTextSize)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Comment text size",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                color = TitleColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = SpacerBackgroundColor
            ) {
                Text(
                    text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Pellentesque habitant morbi tristique senectus et netus.",
                    style = MaterialTheme.typography.bodyLarge.withCommentTextSize(selectedTextSize),
                    color = CommentTextColor,
                    modifier = Modifier.padding(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val sliderSteps = TextSizeDefaults.sliderSteps
            val textSizeOptions = remember {
                buildList {
                    var current = TextSizeDefaults.MinSizeSp
                    while (current <= TextSizeDefaults.MaxSizeSp + 0.01f) {
                        add(TextSizeDefaults.roundToStep(current))
                        current += TextSizeDefaults.StepSizeSp
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val sliderColors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    thumbColor = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = selectedTextSize,
                    onValueChange = { value ->
                        val snapped = TextSizeDefaults.clamp(value)
                        if (selectedTextSize != snapped) {
                            selectedTextSize = snapped
                            onTextSizeChanged(snapped)
                        }
                    },
                    valueRange = TextSizeDefaults.MinSizeSp..TextSizeDefaults.MaxSizeSp,
                    steps = sliderSteps,
                    colors = sliderColors,
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                                    shape = CircleShape
                                )
                        )
                    },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            colors = sliderColors
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${selectedTextSize.roundToInt()} sp",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Theme Preset",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                color = TitleColor
            )

            Spacer(modifier = Modifier.height(24.dp))

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
                onClick = { onSelectionSaved(selectedThemeId, selectedPostCardStyleId, selectedTextSize) },
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
private fun OnboardingPostPreview(postCardStyle: PostCardStyle) {
    com.munch.reddit.feature.feed.RedditPostItem(
        post = onboardingSamplePost,
        selectedSubreddit = "all",
        onSubredditTapped = {},
        onPostSelected = {},
        onImageClick = {},
        onYouTubeSelected = {},
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun OnboardingCommentPreview(textSize: Float) {
    val spacing = MaterialSpacing
    val commentTextStyle = MaterialTheme.typography.bodyMedium.withCommentTextSize(textSize)
    // Match the real CommentItem layout (depth = 0)
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.sm * 0.5f),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        Text(
                            text = "jane_doe",
                            color = SubredditColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        Icon(
                            Icons.Filled.AccessTime,
                            contentDescription = null,
                            tint = MetaInfoColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(text = "1h", color = MetaInfoColor, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(spacing.sm))
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = null,
                            tint = MetaInfoColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(text = "342", color = MetaInfoColor, fontSize = 12.sp)
                    }
                }

                // Body text
                val sample = "This is a sample comment with an image link https://i.redd.it/abcd1234.png and some text."
                LinkifiedText(
                    text = sample,
                    style = commentTextStyle,
                    color = TitleColor.copy(alpha = 0.9f),
                    linkColor = SubredditColor,
                    quoteColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = spacing.xs, bottom = spacing.xs)
                )
            }
        }
    }
}

@Composable
private fun SliderDotsRow(
    sizes: List<Float>,
    selectedSize: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        sizes.forEach { size ->
            val isActive = size <= selectedSize + 0.01f
            Box(
                modifier = Modifier
                    .size(if (isActive) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SelectThemeScreenPreview() {
    MunchForRedditTheme {
        SelectThemeScreen(
            initialThemeId = FeedThemePreset.Narwhal.id,
            initialPostCardStyleId = PostCardStyle.CardV1.id,
            initialTextSize = TextSizeDefaults.DefaultSizeSp,
            onSelectionSaved = { _, _, _ -> }
        )
    }
}
