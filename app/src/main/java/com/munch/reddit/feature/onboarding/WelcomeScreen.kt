package com.munch.reddit.feature.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.munch.reddit.R
import com.munch.reddit.feature.feed.SubredditColor
import com.munch.reddit.feature.feed.TitleColor
import com.munch.reddit.ui.theme.MunchForRedditTheme

@Composable
fun WelcomeScreen(
    onStartClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // App Icon
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "Wormi for Reddit Icon",
                modifier = Modifier.size(120.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Welcome Title
            Text(
                text = "Wormi for Reddit!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TitleColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Developer Credit
            Text(
                text = "Developed by naren.k",
                fontSize = 16.sp,
                color = TitleColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            // Start Button
            Button(
                onClick = onStartClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SubredditColor
                ),
                modifier = Modifier
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Start",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    MunchForRedditTheme {
        WelcomeScreen(onStartClick = {})
    }
}
