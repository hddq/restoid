package io.github.hddq.restoid.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.hddq.restoid.R

private const val RESTIC_FORGET_DOCS_URL = "https://restic.readthedocs.io/en/stable/060_forget.html"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ForgetGuideBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resticForgetDocsIntent = remember {
        Intent(Intent.ACTION_VIEW, Uri.parse(RESTIC_FORGET_DOCS_URL))
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.forget_guide_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.forget_guide_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            ExpressiveSectionHeader(title = stringResource(R.string.forget_guide_section_how_it_works))
            ExpressiveCardGroup {
                ForgetGuideItem(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.forget_guide_retention_title),
                    description = stringResource(R.string.forget_guide_retention_desc),
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 4)
                )
                ForgetGuideItem(
                    icon = Icons.Outlined.Dns,
                    title = stringResource(R.string.forget_guide_hosts_title),
                    description = stringResource(R.string.forget_guide_hosts_desc),
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = 4)
                )
                ForgetGuideItem(
                    icon = Icons.AutoMirrored.Outlined.Label,
                    title = stringResource(R.string.forget_guide_tags_title),
                    description = stringResource(R.string.forget_guide_tags_desc),
                    shapes = ListItemDefaults.segmentedShapes(index = 2, count = 4)
                )
                ForgetGuideItem(
                    icon = Icons.Outlined.Layers,
                    title = stringResource(R.string.forget_guide_grouping_title),
                    description = stringResource(R.string.forget_guide_grouping_desc),
                    shapes = ListItemDefaults.segmentedShapes(index = 3, count = 4)
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .padding(end = 12.dp, top = 2.dp)
                            .size(24.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.tasks_guide_forget_vs_prune_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.forget_guide_forget_vs_prune_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            FilledTonalButton(
                onClick = {
                    try {
                        context.startActivity(resticForgetDocsIntent)
                    } catch (_: ActivityNotFoundException) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("URL", RESTIC_FORGET_DOCS_URL))
                        Toast.makeText(context, context.getString(R.string.no_browser_available), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.forget_guide_docs_button))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ForgetGuideItem(
    icon: ImageVector,
    title: String,
    description: String,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier
) {
    SegmentedListItem(
        shapes = shapes,
        modifier = modifier,
        colors = expressiveListItemColors(),
        verticalAlignment = Alignment.Top,
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        supportingContent = {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall
            )
        },
        content = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    )
}
