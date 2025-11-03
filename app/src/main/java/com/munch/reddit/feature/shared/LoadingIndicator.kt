package com.munch.reddit.feature.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import com.munch.reddit.feature.feed.SpacerBackgroundColor
import com.munch.reddit.feature.feed.SubredditColor
import com.munch.reddit.ui.theme.MunchForRedditTheme
import kotlin.random.Random

/**
 * Generates a random set of polygons for the loading indicator.
 * Creates interesting shape morphing animations by randomly selecting polygon types.
 */
private fun generateRandomPolygons(): List<RoundedPolygon> {
    val random = Random.Default

    // Define possible polygon configurations
    val polygonTypes = listOf(
        // Simple polygons with different vertex counts
        { rounding: Float -> RoundedPolygon(numVertices = 3, rounding = CornerRounding(rounding)) }, // Triangle
        { rounding: Float -> RoundedPolygon(numVertices = 4, rounding = CornerRounding(rounding)) }, // Square
        { rounding: Float -> RoundedPolygon(numVertices = 5, rounding = CornerRounding(rounding)) }, // Pentagon
        { rounding: Float -> RoundedPolygon(numVertices = 6, rounding = CornerRounding(rounding)) }, // Hexagon
        { rounding: Float -> RoundedPolygon(numVertices = 8, rounding = CornerRounding(rounding)) }, // Octagon
        // Star shapes
        { rounding: Float -> RoundedPolygon.star(numVerticesPerRadius = 5, rounding = CornerRounding(rounding)) },
        { rounding: Float -> RoundedPolygon.star(numVerticesPerRadius = 6, rounding = CornerRounding(rounding)) },
        { rounding: Float -> RoundedPolygon.star(numVerticesPerRadius = 7, rounding = CornerRounding(rounding)) },
    )

    // Pick 2-4 random polygon types (at least 2 required for morphing)
    val numShapes = random.nextInt(2, 5)
    val selectedTypes = polygonTypes.shuffled(random).take(numShapes)

    // Generate polygons with random rounding values
    return selectedTypes.map { polygonFactory ->
        val rounding = random.nextFloat() * 0.4f + 0.1f // Between 0.1 and 0.5
        polygonFactory(rounding)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    // Generate random polygons and remember them for this composition
    val randomPolygons = remember { generateRandomPolygons() }

    androidx.compose.material3.LoadingIndicator(
        color = SubredditColor,
        polygons = randomPolygons,
        modifier = modifier
    )
}

@Preview(name = "Loading Indicator - Default Polygons", showBackground = true)
@Composable
private fun LoadingIndicatorPreview() {
    MunchForRedditTheme {
        Column(
            modifier = Modifier
                .size(300.dp)
                .background(SpacerBackgroundColor),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Default Polygons",
                color = SubredditColor,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            LoadingIndicator(modifier = Modifier.size(48.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(name = "Loading Indicator - Triangle to Square", showBackground = true)
@Composable
private fun LoadingIndicatorTriangleSquarePreview() {
    MunchForRedditTheme {
        Column(
            modifier = Modifier
                .size(300.dp)
                .background(SpacerBackgroundColor),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Triangle → Square",
                color = SubredditColor,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            androidx.compose.material3.LoadingIndicator(
                color = SubredditColor,
                polygons = listOf(
                    // Triangle
                    RoundedPolygon(
                        numVertices = 3,
                        rounding = CornerRounding(0.2f)
                    ),
                    // Square
                    RoundedPolygon(
                        numVertices = 4,
                        rounding = CornerRounding(0.2f)
                    )
                ),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(name = "Loading Indicator - Pentagon to Octagon", showBackground = true)
@Composable
private fun LoadingIndicatorPentagonOctagonPreview() {
    MunchForRedditTheme {
        Column(
            modifier = Modifier
                .size(300.dp)
                .background(SpacerBackgroundColor),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Pentagon → Octagon",
                color = SubredditColor,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            androidx.compose.material3.LoadingIndicator(
                color = SubredditColor,
                polygons = listOf(
                    // Pentagon
                    RoundedPolygon(
                        numVertices = 5,
                        rounding = CornerRounding(0.3f)
                    ),
                    // Octagon
                    RoundedPolygon(
                        numVertices = 8,
                        rounding = CornerRounding(0.3f)
                    )
                ),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(name = "Loading Indicator - Star Shapes", showBackground = true)
@Composable
private fun LoadingIndicatorStarPreview() {
    MunchForRedditTheme {
        Column(
            modifier = Modifier
                .size(300.dp)
                .background(SpacerBackgroundColor),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Star (5-point → 6-point)",
                color = SubredditColor,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            androidx.compose.material3.LoadingIndicator(
                color = SubredditColor,
                polygons = listOf(
                    // 5-point star
                    RoundedPolygon.star(
                        numVerticesPerRadius = 5,
                        rounding = CornerRounding(0.1f)
                    ),
                    // 6-point star
                    RoundedPolygon.star(
                        numVerticesPerRadius = 6,
                        rounding = CornerRounding(0.1f)
                    )
                ),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(name = "Loading Indicator - Square to Rounded Square", showBackground = true)
@Composable
private fun LoadingIndicatorRoundedSquarePreview() {
    MunchForRedditTheme {
        Column(
            modifier = Modifier
                .size(300.dp)
                .background(SpacerBackgroundColor),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Sharp Square → Rounded Square",
                color = SubredditColor,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            androidx.compose.material3.LoadingIndicator(
                color = SubredditColor,
                polygons = listOf(
                    // Sharp square
                    RoundedPolygon(
                        numVertices = 4,
                        rounding = CornerRounding(0.0f)
                    ),
                    // Very rounded square
                    RoundedPolygon(
                        numVertices = 4,
                        rounding = CornerRounding(0.5f, smoothing = 0.8f)
                    )
                ),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(name = "Loading Indicator - Multi-Shape Sequence", showBackground = true)
@Composable
private fun LoadingIndicatorMultiShapePreview() {
    MunchForRedditTheme {
        Column(
            modifier = Modifier
                .size(300.dp)
                .background(SpacerBackgroundColor),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Triangle → Square → Pentagon → Hexagon",
                color = SubredditColor,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            androidx.compose.material3.LoadingIndicator(
                color = SubredditColor,
                polygons = listOf(
                    RoundedPolygon(numVertices = 3, rounding = CornerRounding(0.2f)),
                    RoundedPolygon(numVertices = 4, rounding = CornerRounding(0.2f)),
                    RoundedPolygon(numVertices = 5, rounding = CornerRounding(0.2f)),
                    RoundedPolygon(numVertices = 6, rounding = CornerRounding(0.2f))
                ),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Preview(name = "Loading State - Full Screen", showBackground = true)
@Composable
private fun LoadingStatePreview() {
    MunchForRedditTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SpacerBackgroundColor),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator()
        }
    }
}
