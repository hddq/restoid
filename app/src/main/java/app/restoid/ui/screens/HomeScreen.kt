package app.restoid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.restoid.data.PasswordManager
import app.restoid.data.RepositoriesRepository
import app.restoid.data.ResticRepository
import app.restoid.data.ResticState
import app.restoid.data.SnapshotInfo
import app.restoid.ui.components.PasswordDialog
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val repositoriesRepository = remember { RepositoriesRepository(context) }
    val resticRepository = remember { ResticRepository(context) }
    val passwordManager = remember { PasswordManager(context) }
    val coroutineScope = rememberCoroutineScope()
    
    val selectedRepo by repositoriesRepository.selectedRepository.collectAsState()
    val resticState by resticRepository.resticState.collectAsState()
    
    var snapshots by remember { mutableStateOf<List<SnapshotInfo>>(emptyList()) }
    var isLoadingSnapshots by remember { mutableStateOf(false) }
    var snapshotError by remember { mutableStateOf<String?>(null) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var repositoryNeedingPassword by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        repositoriesRepository.loadRepositories()
        resticRepository.checkResticStatus()
    }
    
    // Load snapshots when we have a selected repo and restic is installed
    LaunchedEffect(selectedRepo, resticState) {
        val currentRepo = selectedRepo
        val currentState = resticState
        if (currentRepo != null && currentState is ResticState.Installed) {
            val storedPassword = repositoriesRepository.getRepositoryPassword(currentRepo)
            if (storedPassword != null) {
                isLoadingSnapshots = true
                snapshotError = null
                try {
                    val result = resticRepository.getSnapshots(currentRepo, storedPassword)
                    result.fold(
                        onSuccess = { snapshots = it },
                        onFailure = { snapshotError = it.message ?: "Failed to load snapshots" }
                    )
                } catch (e: Exception) {
                    snapshotError = e.message ?: "Failed to load snapshots"
                } finally {
                    isLoadingSnapshots = false
                }
            } else {
                // No password stored, show password dialog
                repositoryNeedingPassword = currentRepo
                showPasswordDialog = true
                snapshotError = "Password required for repository"
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Restoid title at top left - Material 3 style
        Text(
            text = "Restoid",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        when {
            selectedRepo == null -> {
                Text(
                    text = "No repository selected",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            resticState !is ResticState.Installed -> {
                Text(
                    text = "Restic not available",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
            isLoadingSnapshots -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Text(
                    text = "Loading snapshots...",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp)
                )
            }
            snapshotError != null -> {
                Text(
                    text = "Error: $snapshotError",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
            snapshots.isEmpty() -> {
                Text(
                    text = "No snapshots found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                Text(
                    text = "Snapshots (${snapshots.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                LazyColumn {
                    items(snapshots) { snapshot ->
                        SnapshotCard(snapshot = snapshot)
                    }
                }
            }
        }
    }
    
    // Password dialog
    if (showPasswordDialog && repositoryNeedingPassword != null) {
        PasswordDialog(
            title = "Repository Password Required",
            message = "Please enter the password for repository: ${repositoryNeedingPassword}",
            onPasswordEntered = { password ->
                val repoPath = repositoryNeedingPassword!!
                showPasswordDialog = false
                repositoryNeedingPassword = null

                // Load snapshots with the provided password
                isLoadingSnapshots = true
                snapshotError = null

                // Use a coroutine to load snapshots
                coroutineScope.launch {
                    try {
                        val result = resticRepository.getSnapshots(repoPath, password)
                        result.fold(
                            onSuccess = { snapshots = it },
                            onFailure = { snapshotError = it.message ?: "Failed to load snapshots" }
                        )
                    } catch (e: Exception) {
                        snapshotError = e.message ?: "Failed to load snapshots"
                    } finally {
                        isLoadingSnapshots = false
                    }
                }
            },
            onDismiss = {
                showPasswordDialog = false
                repositoryNeedingPassword = null
            }
        )
    }
}

@Composable
private fun SnapshotCard(snapshot: SnapshotInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = snapshot.id.take(8),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = snapshot.time,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (snapshot.paths.isNotEmpty()) {
                Text(
                    text = "Paths: ${snapshot.paths.take(3).joinToString(", ")}${if (snapshot.paths.size > 3) "..." else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}