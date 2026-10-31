package io.github.hddq.restoid.ui.screens

import android.content.ClipData
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import io.github.hddq.restoid.R
import io.github.hddq.restoid.ui.components.ExpressiveSegmentedSurface
import kotlinx.coroutines.launch

@Composable
fun LicensesScreen(onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val libs = remember {
        try {
            val jsonString = context.resources.openRawResource(R.raw.aboutlibraries)
                .bufferedReader()
                .use { it.readText() }
            Libs.Builder().withJson(jsonString).build()
        } catch (e: Exception) {
            Libs.Builder().build()
        }
    }

    val libraries = remember(libs) { libs.libraries.sortedBy { it.name } }
    val totalCount = libraries.size + 2

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)
    ) {
        item(key = "restoid_license") {
            ExpressiveSegmentedSurface(index = 0, count = totalCount) {
                LicenseCard(
                    name = stringResource(R.string.app_name),
                    author = stringResource(R.string.license_restoid_author),
                    licenseName = stringResource(R.string.license_restoid_name),
                    licenseText = Licenses.RESTOID_LICENSE,
                    url = stringResource(R.string.license_restoid_url)
                )
            }
        }
        item(key = "restic_license") {
            ExpressiveSegmentedSurface(index = 1, count = totalCount) {
                LicenseCard(
                    name = stringResource(R.string.license_restic_name),
                    author = stringResource(R.string.license_restic_author),
                    licenseName = stringResource(R.string.license_restic_license),
                    licenseText = Licenses.RESTIC_LICENSE,
                    url = stringResource(R.string.license_restic_url)
                )
            }
        }

        itemsIndexed(libraries, key = { index, library -> "${library.uniqueId}_$index" }) { index, library ->
            val firstLicense = library.licenses.firstOrNull()
            val licenseUrl = firstLicense?.url ?: ""
            val website = library.website ?: ""
            val primaryUrl = if (licenseUrl.isNotBlank()) licenseUrl else website

            ExpressiveSegmentedSurface(index = index + 2, count = totalCount) {
                LicenseCard(
                    name = library.name,
                    author = library.developers.firstOrNull()?.name ?: library.organization?.name ?: stringResource(R.string.license_unknown_author),
                    licenseName = firstLicense?.name ?: stringResource(R.string.license_unknown_name),
                    licenseText = if (licenseUrl.isNotBlank()) {
                        stringResource(R.string.license_url_label, licenseUrl)
                    } else if (website.isNotBlank()) {
                        stringResource(R.string.license_project_website_label, website)
                    } else {
                        stringResource(R.string.license_no_link_available)
                    },
                    version = library.artifactVersion,
                    url = primaryUrl
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LicenseCard(
    name: String,
    author: String = "",
    licenseName: String = "",
    licenseText: String,
    version: String? = null,
    url: String = ""
) {
    var expanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val tapToExpand = stringResource(R.string.license_tap_to_expand)

    val licensePreview = remember(licenseText, tapToExpand) {
        if (licenseText.length > 200) {
            licenseText.trimIndent().take(200) + tapToExpand
        } else {
            licenseText.trimIndent()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, "$name\n$url\n\n$licenseText")))
                    }
                }
            )
            .padding(16.dp)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (author.isNotBlank()) {
                    Text(author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (version != null) {
                    Text(version, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
            }

            Row {
                if (url.isNotBlank()) {
                    IconButton(
                        onClick = { uriHandler.openUri(url) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.cd_open_link),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        if (licenseName.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(licenseName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (expanded || licenseText.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (expanded) licenseText.trimIndent() else licensePreview,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (expanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, "$name\n$url\n\n$licenseText")))
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.cd_copy_license),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
