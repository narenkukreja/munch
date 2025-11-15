package com.munch.reddit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.munch.reddit.activity.AuthActivity
import com.munch.reddit.activity.FeedActivity
import com.munch.reddit.data.AppPreferences

/**
 * Main launcher activity that routes to the appropriate screen based on onboarding state.
 * This activity acts as a router and immediately launches the correct Activity.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appPreferences = AppPreferences(this)
        val hasCompletedOnboarding = appPreferences.hasCompletedOnboarding

        // Route to appropriate activity
        val targetActivity = if (hasCompletedOnboarding) {
            FeedActivity::class.java
        } else {
            AuthActivity::class.java
        }

        val intent = Intent(this, targetActivity)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
