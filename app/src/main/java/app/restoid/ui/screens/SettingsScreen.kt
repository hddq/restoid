package app.restoid.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.restoid.ui.settings.RootState
import app.restoid.ui.settings.SettingsViewModel

@Composable
fun SettingsScreen(
    // Get an instance of our ViewModel
    settingsViewModel: SettingsViewModel = viewModel()
) {
    // Observe the rootState from the ViewModel.
    // The UI will automatically recompose when the state changes.
    val rootState by settingsViewModel.rootState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // AnimatedContent provides a nice fade-in/fade-out animation when the state changes.
        AnimatedContent(targetState = rootState, label = "RootStatusAnimation") { state ->
            when (state) {
                RootState.Idle -> RootRequestRow(onClick = { settingsViewModel.checkRootAccess(prompt = true) })
                RootState.Checking -> CircularProgressIndicator()
                RootState.Granted -> RootStatusRow(
                    text = "Root access granted",
                    icon = Icons.Default.CheckCircle
                )
                RootState.Denied -> RootRequestRow(
                    text = "Root access denied",
                    buttonText = "Try Again",
                    icon = Icons.Default.Error,
                    onClick = { settingsViewModel.checkRootAccess(prompt = true) }
                )
            }
        }
    }
}

@Composable
fun RootRequestRow(
    text: String = "Root access is required for app backups.",
    buttonText: String = "Grant",
    icon: ImageVector? = null,
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
            tint = MaterialTheme.colorScheme.primary, // Expressive color
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}