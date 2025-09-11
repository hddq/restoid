package app.restoid.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.restoid.RestoidApplication
import app.restoid.data.RootState
import app.restoid.ui.settings.SettingsViewModel
import app.restoid.ui.settings.SettingsViewModelFactory

@Composable
fun SettingsScreen() {
    val application = LocalContext.current.applicationContext as RestoidApplication
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(application.rootRepository)
    )

    val rootState by settingsViewModel.rootState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedContent(targetState = rootState, label = "RootStatusAnimation") { state ->
            when (state) {
                // THE FIX IS HERE: I removed the reference to the non-existent 'Idle' state.
                RootState.Denied -> RootRequestRow(
                    text = "Root access denied",
                    buttonText = "Try Again",
                    icon = Icons.Default.Error,
                    onClick = { settingsViewModel.requestRootAccess() }
                )
                RootState.Checking -> CircularProgressIndicator()
                RootState.Granted -> RootStatusRow(
                    text = "Root access granted",
                    icon = Icons.Default.CheckCircle
                )
            }
        }
    }
}

// Helper composables below are correct and do not need changes.

@Composable
fun RootRequestRow(
    text: String,
    buttonText: String,
    icon: ImageVector?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        Text(text = text, modifier = Modifier.weight(1f))
        Button(onClick = onClick) {
            Text(buttonText)
        }
    }
}

@Composable
fun RootStatusRow(text: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}