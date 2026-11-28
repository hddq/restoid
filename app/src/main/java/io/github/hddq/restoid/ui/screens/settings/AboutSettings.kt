package io.github.hddq.restoid.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.hddq.restoid.R
import io.github.hddq.restoid.ui.components.ExpressiveCardGroup
import io.github.hddq.restoid.ui.components.ExpressiveSectionHeader
import io.github.hddq.restoid.ui.components.expressiveListItemColors

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutSettings(onNavigateToLicenses: () -> Unit) {
    val context = LocalContext.current
    val versionText = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = packageInfo.versionName ?: context.getString(R.string.not_available)
            val versionCode = packageInfo.longVersionCode
            "$versionName ($versionCode)"
        } catch (e: Exception) {
            context.getString(R.string.not_available)
        }
    }
    val githubIntent = remember { Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hddq/restoid")) }
    val issuesIntent = remember { Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hddq/restoid/issues/new/choose")) }
    val crowdinIntent = remember { Intent(Intent.ACTION_VIEW, Uri.parse("https://crowdin.com/project/restoid")) }
    val launchExternalIntent = remember(context) {
        { intent: Intent ->
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                // No handler available for this intent.
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        ExpressiveSectionHeader(title = stringResource(R.string.about_title))
        ExpressiveCardGroup {
            SegmentedListItem(
                onClick = onNavigateToLicenses,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 4),
                verticalAlignment = Alignment.CenterVertically,
                colors = expressiveListItemColors(),
                leadingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Notes,
                        contentDescription = null
                    )
                },
                content = { Text(stringResource(R.string.open_source_licenses)) }
            )

            SegmentedListItem(
                onClick = { launchExternalIntent(crowdinIntent) },
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 4),
                verticalAlignment = Alignment.CenterVertically,
                colors = expressiveListItemColors(),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Translate,
                        contentDescription = null
                    )
                },
                content = { Text(stringResource(R.string.translate)) }
            )

            SegmentedListItem(
                onClick = { launchExternalIntent(issuesIntent) },
                shapes = ListItemDefaults.segmentedShapes(index = 2, count = 4),
                verticalAlignment = Alignment.CenterVertically,
                colors = expressiveListItemColors(),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Feedback,
                        contentDescription = null
                    )
                },
                content = { Text(stringResource(R.string.issues_and_feedback)) }
            )

            SegmentedListItem(
                onClick = { launchExternalIntent(githubIntent) },
                shapes = ListItemDefaults.segmentedShapes(index = 3, count = 4),
                verticalAlignment = Alignment.CenterVertically,
                colors = expressiveListItemColors(),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null
                    )
                },
                supportingContent = { Text(versionText) },
                content = { Text(stringResource(R.string.version_label)) }
            )
        }
    }
}
