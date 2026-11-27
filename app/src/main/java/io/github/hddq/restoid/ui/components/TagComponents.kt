package io.github.hddq.restoid.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hddq.restoid.R
import io.github.hddq.restoid.model.BackupTag
import io.github.hddq.restoid.model.BackupTagRules
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TagColorDot(
    colorArgb: Int,
    size: Dp = 12.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .background(Color(colorArgb), CircleShape)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveTagChip(
    label: String,
    selected: Boolean,
    tagColorArgb: Int?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val motionScheme = MaterialTheme.motionScheme
    val cornerRadius by animateDpAsState(
        targetValue = if (selected) 10.dp else 16.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "tag_chip_corner_radius"
    )
    val targetContainerColor = if (tagColorArgb != null) {
        val tagColor = Color(tagColorArgb)
        if (selected) tagColor else tagColor.copy(alpha = 0.16f)
    } else {
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val targetBorderColor = if (tagColorArgb != null) {
        val tagColor = Color(tagColorArgb)
        if (selected) tagColor else tagColor.copy(alpha = 0.45f)
    } else {
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.outlineVariant
    }
    val targetContentColor = if (tagColorArgb != null) {
        val tagColor = Color(tagColorArgb)
        if (selected) {
            if (tagColor.luminance() > 0.179f) Color.Black else Color.White
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    } else {
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    }

    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "tag_chip_container_color"
    )
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "tag_chip_border_color"
    )
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "tag_chip_content_color"
    )

    val chipContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(visible = selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (onClick != null) {
        Surface(
            selected = selected,
            onClick = onClick,
            modifier = modifier.height(32.dp),
            shape = RoundedCornerShape(cornerRadius),
            color = containerColor,
            contentColor = contentColor,
            border = BorderStroke(1.dp, borderColor),
            content = chipContent
        )
    } else {
        Surface(
            modifier = modifier.height(32.dp),
            shape = RoundedCornerShape(cornerRadius),
            color = containerColor,
            contentColor = contentColor,
            border = BorderStroke(1.dp, borderColor),
            content = chipContent
        )
    }
}

private data class TagPickerEntry(
    val key: String,
    val name: String,
    val tag: BackupTag?,
    val isSelected: Boolean
)

@Composable
fun BackupTagsRow(
    selectedTags: List<String>,
    availableTags: List<BackupTag>,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subtitle = if (selectedTags.isEmpty()) {
        stringResource(R.string.tags_none)
    } else {
        selectedTags.joinToString(", ")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Label,
            contentDescription = null,
            tint = if (selectedTags.isNotEmpty()) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(end = 16.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.run_tasks_tags),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupTagPickerSheet(
    availableTags: List<BackupTag>,
    selectedTags: List<String>,
    onToggleTag: (String) -> Unit,
    onCreateTag: suspend (name: String, colorArgb: Int) -> Result<BackupTag>,
    onUpdateTagColor: suspend (name: String, colorArgb: Int) -> Result<BackupTag>,
    onDeleteTag: suspend (name: String) -> Result<Unit>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.run_tasks_tags)
) {
    val scope = rememberCoroutineScope()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<BackupTag?>(null) }
    var deletingTag by remember { mutableStateOf<BackupTag?>(null) }
    var isMutating by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                FilledTonalButton(
                    onClick = { showCreateDialog = true },
                    enabled = !isMutating
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_new_tag))
                }
            }

            val orphanedSelectedTags = selectedTags.filter { selected ->
                availableTags.none { it.name.equals(selected, ignoreCase = true) }
            }
            val entries = availableTags.map { tag ->
                TagPickerEntry(
                    key = tag.name,
                    name = tag.name,
                    tag = tag,
                    isSelected = selectedTags.any { it.equals(tag.name, ignoreCase = true) }
                )
            } + orphanedSelectedTags.map { tagName ->
                TagPickerEntry(
                    key = "orphan_$tagName",
                    name = tagName,
                    tag = null,
                    isSelected = true
                )
            }

            if (entries.isEmpty()) {
                ExpressiveEmptyState(
                    icon = Icons.AutoMirrored.Outlined.Label,
                    title = stringResource(R.string.tags_empty_title),
                    subtitle = stringResource(R.string.tags_empty_hint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(items = entries, key = { _, entry -> entry.key }) { index, entry ->
                        ExpressiveSegmentedSurface(
                            index = index,
                            count = entries.size
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = entry.isSelected,
                                        enabled = !isMutating,
                                        role = Role.Checkbox,
                                        onValueChange = { onToggleTag(entry.name) }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    ExpressiveTagChip(
                                        label = entry.name,
                                        selected = entry.isSelected,
                                        tagColorArgb = entry.tag?.colorArgb
                                    )
                                }
                                entry.tag?.let { trackedTag ->
                                    IconButton(
                                        onClick = { editingTag = trackedTag },
                                        enabled = !isMutating
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Palette,
                                            contentDescription = stringResource(R.string.action_edit_tag_color)
                                        )
                                    }
                                    IconButton(
                                        onClick = { deletingTag = trackedTag },
                                        enabled = !isMutating
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = stringResource(R.string.action_delete_tag),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    var createError by remember { mutableStateOf<String?>(null) }
    var editError by remember { mutableStateOf<String?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val defaultErrorMessage = stringResource(R.string.error_unknown)

    if (showCreateDialog) {
        TagColorEditorDialog(
            initialTag = null,
            existingTagNames = availableTags.map { it.name },
            isSaving = isMutating,
            errorMessage = createError,
            onDismissRequest = {
                if (!isMutating) {
                    createError = null
                    showCreateDialog = false
                }
            },
            onConfirm = { name, colorArgb ->
                isMutating = true
                createError = null
                scope.launch {
                    val result = onCreateTag(name, colorArgb)
                    isMutating = false
                    if (result.isSuccess) {
                        createError = null
                        showCreateDialog = false
                    } else {
                        createError = result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() } ?: defaultErrorMessage
                    }
                }
            }
        )
    }

    editingTag?.let { tag ->
        TagColorEditorDialog(
            initialTag = tag,
            existingTagNames = availableTags.map { it.name },
            isSaving = isMutating,
            errorMessage = editError,
            onDismissRequest = {
                if (!isMutating) {
                    editError = null
                    editingTag = null
                }
            },
            onConfirm = { _, colorArgb ->
                isMutating = true
                editError = null
                scope.launch {
                    val result = onUpdateTagColor(tag.name, colorArgb)
                    isMutating = false
                    if (result.isSuccess) {
                        editError = null
                        editingTag = null
                    } else {
                        editError = result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() } ?: defaultErrorMessage
                    }
                }
            }
        )
    }

    deletingTag?.let { tag ->
        AlertDialog(
            onDismissRequest = {
                if (!isMutating) {
                    deleteError = null
                    deletingTag = null
                }
            },
            title = { Text(stringResource(R.string.dialog_delete_tag_title, tag.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.dialog_delete_tag_message))
                    if (deleteError != null) {
                        Text(
                            text = deleteError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isMutating = true
                        deleteError = null
                        scope.launch {
                            val result = onDeleteTag(tag.name)
                            isMutating = false
                            if (result.isSuccess) {
                                deleteError = null
                                deletingTag = null
                            } else {
                                deleteError = result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() } ?: defaultErrorMessage
                            }
                        }
                    },
                    enabled = !isMutating
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deleteError = null
                        deletingTag = null
                    },
                    enabled = !isMutating
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagColorEditorDialog(
    initialTag: BackupTag?,
    existingTagNames: List<String>,
    isSaving: Boolean = false,
    errorMessage: String? = null,
    onDismissRequest: () -> Unit,
    onConfirm: (name: String, colorArgb: Int) -> Unit
) {
    val isEditMode = initialTag != null
    var name by remember(initialTag) { mutableStateOf(initialTag?.name.orEmpty()) }
    var colorArgb by remember(initialTag) {
        mutableIntStateOf(initialTag?.colorArgb ?: BackupTagRules.SUGGESTED_COLOR_ARGBS.first())
    }
    var hexText by remember(initialTag) { mutableStateOf(formatRgbHex(colorArgb)) }

    val red = (colorArgb shr 16) and 0xFF
    val green = (colorArgb shr 8) and 0xFF
    val blue = colorArgb and 0xFF

    val updateColor: (Int) -> Unit = { newArgb ->
        colorArgb = newArgb
        hexText = formatRgbHex(newArgb)
    }

    val normalizedName = BackupTagRules.normalizeTagName(name)
    val validationError = if (isEditMode) {
        null
    } else {
        when (BackupTagRules.validateTagName(name)) {
            BackupTagRules.TagValidationError.EMPTY -> stringResource(R.string.tag_error_name_empty)
            BackupTagRules.TagValidationError.CONTAINS_COMMA -> stringResource(R.string.tag_error_name_comma)
            BackupTagRules.TagValidationError.RESERVED -> stringResource(R.string.tag_error_name_reserved)
            null -> if (existingTagNames.any { it.equals(normalizedName, ignoreCase = true) }) {
                stringResource(R.string.tag_error_name_duplicate)
            } else {
                null
            }
        }
    }
    val isHexValid = parseRgbHex(hexText) != null
    val canConfirm = !isSaving && isHexValid && (isEditMode || validationError == null)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = if (isEditMode) {
                    stringResource(R.string.dialog_edit_tag_color_title)
                } else {
                    stringResource(R.string.dialog_new_tag_title)
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val previewLabel = normalizedName.ifEmpty { stringResource(R.string.tag_preview_placeholder) }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpressiveTagChip(
                        label = previewLabel,
                        selected = false,
                        tagColorArgb = colorArgb
                    )
                    ExpressiveTagChip(
                        label = previewLabel,
                        selected = true,
                        tagColorArgb = colorArgb
                    )
                }

                if (!isEditMode) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.tag_name_label)) },
                        singleLine = true,
                        isError = name.isNotEmpty() && validationError != null,
                        supportingText = if (name.isNotEmpty() && validationError != null) {
                            { Text(validationError) }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BackupTagRules.SUGGESTED_COLOR_ARGBS.forEach { swatchArgb ->
                        val isSelected = swatchArgb == colorArgb
                        val swatchColor = Color(swatchArgb)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(swatchColor, CircleShape)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape
                                )
                                .clickable { updateColor(swatchArgb) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = if (swatchColor.luminance() > 0.5f) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                RgbChannelSlider(
                    label = stringResource(R.string.tag_color_red),
                    value = red,
                    onValueChange = { r -> updateColor(packRgb(r, green, blue)) }
                )
                RgbChannelSlider(
                    label = stringResource(R.string.tag_color_green),
                    value = green,
                    onValueChange = { g -> updateColor(packRgb(red, g, blue)) }
                )
                RgbChannelSlider(
                    label = stringResource(R.string.tag_color_blue),
                    value = blue,
                    onValueChange = { b -> updateColor(packRgb(red, green, b)) }
                )

                OutlinedTextField(
                    value = hexText,
                    onValueChange = { input ->
                        hexText = input
                        parseRgbHex(input)?.let { parsed ->
                            colorArgb = parsed
                        }
                    },
                    label = { Text(stringResource(R.string.tag_color_hex_label)) },
                    singleLine = true,
                    isError = !isHexValid,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(normalizedName, colorArgb) },
                enabled = canConfirm
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !isSaving
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun RgbChannelSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f
        )
    }
}

private fun packRgb(r: Int, g: Int, b: Int): Int {
    return (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
}

private fun formatRgbHex(argb: Int): String {
    return String.format("#%06X", argb and 0xFFFFFF)
}

private fun parseRgbHex(raw: String): Int? {
    val cleaned = raw.trim().removePrefix("#")
    if (cleaned.length != 6) return null
    val rgb = cleaned.toIntOrNull(16) ?: return null
    return (0xFF shl 24) or rgb
}
