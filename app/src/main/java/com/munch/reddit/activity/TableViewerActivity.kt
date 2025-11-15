package com.munch.reddit.activity

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.munch.reddit.data.AppPreferences
import com.munch.reddit.feature.feed.FeedTheme
import com.munch.reddit.feature.shared.RedditHtmlTable
import com.munch.reddit.feature.shared.TableViewerScreen
import com.munch.reddit.theme.FeedThemePreset
import com.munch.reddit.ui.theme.MunchForRedditTheme

class TableViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)

        val postTitle = intent.getStringExtra(EXTRA_POST_TITLE).orEmpty()
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
        val tables = intent.readTablePayload() ?: run {
            finish()
            return
        }

        setContent {
            val context = this@TableViewerActivity
            val appPreferences = remember { AppPreferences(context) }
            var feedThemeId by remember { mutableStateOf(appPreferences.selectedTheme) }
            val feedThemePreset = remember(feedThemeId) { FeedThemePreset.fromId(feedThemeId) }

            MunchForRedditTheme {
                FeedTheme(feedThemePreset) {
                    TableViewerScreen(
                        postTitle = postTitle,
                        tables = tables,
                        startIndex = startIndex,
                        onBackClick = { finish() }
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.readTablePayload(): List<RedditHtmlTable>? {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSerializableExtra(EXTRA_TABLES, ArrayList::class.java)
        } else {
            getSerializableExtra(EXTRA_TABLES)
        } ?: return null
        return (raw as? ArrayList<*>)?.filterIsInstance<RedditHtmlTable>()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val EXTRA_POST_TITLE = "extra_post_title"
        private const val EXTRA_TABLES = "extra_tables"
        private const val EXTRA_START_INDEX = "extra_start_index"

        fun createIntent(
            context: Context,
            postTitle: String,
            tables: List<RedditHtmlTable>,
            startIndex: Int = 0
        ): Intent {
            return Intent(context, TableViewerActivity::class.java).apply {
                putExtra(EXTRA_POST_TITLE, postTitle)
                putExtra(EXTRA_START_INDEX, startIndex)
                putExtra(EXTRA_TABLES, ArrayList(tables))
            }
        }
    }
}
