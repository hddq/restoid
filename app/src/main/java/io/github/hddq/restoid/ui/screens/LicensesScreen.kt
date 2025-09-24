package io.github.hddq.restoid.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun LicensesScreen(onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    // Scaffold is removed. We use the modifier from the NavHost.
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            LicenseCard(name = "Restoid", licenseText = Licenses.RESTOID_LICENSE)
        }
        item {
            LicenseCard(name = "Restic", licenseText = Licenses.RESTIC_LICENSE)
        }
    }
}

@Composable
fun LicenseCard(name: String, licenseText: String) {
    var expanded by remember { mutableStateOf(false) }
    val licensePreview = remember(licenseText) {
        licenseText.trimIndent().lines().take(15).joinToString("\n") + "\n\n... (tap to see full license)"
    }

    Card(
        modifier = Modifier.clickable { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .animateContentSize() // Animate the size change
        ) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (expanded) licenseText.trimIndent() else licensePreview,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
