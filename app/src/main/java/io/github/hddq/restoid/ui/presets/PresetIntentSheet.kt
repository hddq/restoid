package io.github.hddq.restoid.ui.presets

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.hddq.restoid.R
import io.github.hddq.restoid.ui.components.ExpressiveCardGroup
import io.github.hddq.restoid.ui.components.expressiveListItemColors
import io.github.hddq.restoid.work.PresetIntentReceiver
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PresetIntentSheet(
    presetId: String,
    presetName: String,
    repositoryName: String?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val packageName = context.packageName
    val receiverClass = PresetIntentReceiver::class.java.name
    val adbCommand = remember(packageName, receiverClass, presetId) {
        buildPresetIntentAdbCommand(
            packageName = packageName,
            receiverClass = receiverClass,
            presetId = presetId
        )
    }

    val fields = buildList {
        add(stringResource(R.string.preset_intent_field_action) to PresetIntentReceiver.ACTION_RUN_PRESET)
        add(stringResource(R.string.preset_intent_field_package) to packageName)
        add(stringResource(R.string.preset_intent_field_receiver) to receiverClass)
        add(stringResource(R.string.preset_intent_field_extra, PresetIntentReceiver.EXTRA_PRESET_ID) to presetId)
        if (presetName.isNotBlank()) {
            add(stringResource(R.string.preset_intent_field_extra, PresetIntentReceiver.EXTRA_PRESET_NAME) to presetName)
        }
        repositoryName?.takeIf { it.isNotBlank() }?.let { repo ->
            add(stringResource(R.string.preset_intent_field_extra_optional, PresetIntentReceiver.EXTRA_REPOSITORY) to repo)
        }
    }

    fun copyField(label: String, value: String) {
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value)))
            Toast.makeText(
                context,
                context.getString(R.string.preset_intent_copied, label),
                Toast.LENGTH_SHORT
            ).show()
        }
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
                text = stringResource(R.string.preset_intent_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.preset_intent_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            ExpressiveCardGroup {
                fields.forEachIndexed { index, (label, value) ->
                    SegmentedListItem(
                        onClick = { copyField(label, value) },
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = fields.size),
                        verticalAlignment = Alignment.CenterVertically,
                        colors = expressiveListItemColors(),
                        supportingContent = {
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = stringResource(R.string.cd_copy_value, label),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        content = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val adbLabel = stringResource(R.string.preset_intent_adb_command)
            FilledTonalButton(
                onClick = { copyField(adbLabel, adbCommand) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.action_copy_adb_command))
            }
        }
    }
}

internal fun buildPresetIntentAdbCommand(
    packageName: String,
    receiverClass: String,
    presetId: String
): String {
    val escapedPresetId = presetId.replace("'", "'\"'\"'")
    return "adb shell am broadcast -a ${PresetIntentReceiver.ACTION_RUN_PRESET} " +
        "-n $packageName/$receiverClass " +
        "--es ${PresetIntentReceiver.EXTRA_PRESET_ID} '$escapedPresetId'"
}
