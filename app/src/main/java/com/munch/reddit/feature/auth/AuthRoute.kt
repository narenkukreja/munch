package com.munch.reddit.feature.auth

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.munch.reddit.data.auth.OAuthCallbackHandler
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun AuthRoute(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = koinViewModel(),
    callbackHandler: OAuthCallbackHandler = koinInject(),
    onAuthorized: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    LaunchedEffect(callbackHandler) {
        callbackHandler.callbacks.collect { uri ->
            viewModel.handleAuthorizationRedirect(uri)
        }
    }

    LaunchedEffect(uiState.isAuthorized) {
        if (uiState.isAuthorized) {
            onAuthorized()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Connect to Reddit",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "1. Visit reddit.com/prefs/apps\n2. Create an Installed App\n3. Set redirect URI to com.munch.reddit://oauth\n4. Copy the client ID and paste it below.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = uiState.clientIdInput,
            onValueChange = viewModel::onClientIdChanged,
            label = { Text("Client ID") },
            modifier = Modifier.fillMaxWidth()
        )
        if (!uiState.errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                val request = viewModel.prepareAuthorization() ?: return@Button
                val customTabsIntent = CustomTabsIntent.Builder().build()
                // Prefer Chrome if available to ensure browser handles the OAuth flow
                runCatching {
                    val chrome = "com.android.chrome"
                    context.packageManager.getApplicationInfo(chrome, 0)
                    customTabsIntent.intent.setPackage(chrome)
                }
                customTabsIntent.launchUrl(context, request.uri)
            },
            modifier = Modifier
                .fillMaxWidth(),
            enabled = !uiState.isLoading
        ) {
            Text(text = "Authorize with Reddit")
        }

        if (uiState.isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
