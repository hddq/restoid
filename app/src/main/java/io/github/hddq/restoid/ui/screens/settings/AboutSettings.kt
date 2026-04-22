package io.github.hddq.restoid.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hddq.restoid.BuildConfig
import io.github.hddq.restoid.R

@Composable
fun AboutSettings(onNavigateToLicenses: () -> Unit) {
    val context = LocalContext.current
    val versionText = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = packageInfo.versionName ?: context.getString(R.string.not_available)
            val versionCode =
                packageInfo.longVersionCode
            // Added flavor here, capitalizing it for better looks
            val flavor = BuildConfig.FLAVOR.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
            "$versionName ($versionCode) - $flavor"
        } catch (e: Exception) {
            context.getString(R.string.not_available)
        }
    }
    val githubIntent = remember { Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hddq/restoid")) }


    Column {
        Text(
            text = stringResource(R.string.about_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToLicenses() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Notes,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    Text(stringResource(R.string.open_source_licenses), style = MaterialTheme.typography.bodyLarge)
                }

                Divider(color = MaterialTheme.colorScheme.background)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { context.startActivity(githubIntent) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    Column {
                        Text(stringResource(R.string.version_label), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            versionText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
