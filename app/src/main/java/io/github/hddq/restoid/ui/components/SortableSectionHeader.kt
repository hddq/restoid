package io.github.hddq.restoid.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.hddq.restoid.R
import io.github.hddq.restoid.ui.shared.AppSortOrder

@Composable
fun SortableSectionHeader(
    title: String,
    sortOrder: AppSortOrder,
    onSortOrderChange: (AppSortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpressiveSectionHeader(
            title = title,
            modifier = Modifier.weight(1f)
        )
        Box {
            IconButton(onClick = { sortMenuExpanded = true }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Sort,
                    contentDescription = stringResource(R.string.cd_sort_apps)
                )
            }
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false }
            ) {
                val nameSelected = sortOrder == AppSortOrder.NAME_ASCENDING ||
                    sortOrder == AppSortOrder.NAME_DESCENDING
                SortMenuItem(
                    text = stringResource(R.string.sort_by_name),
                    selected = nameSelected,
                    directionIcon = when {
                        !nameSelected -> null
                        sortOrder == AppSortOrder.NAME_ASCENDING -> Icons.Outlined.ArrowUpward
                        else -> Icons.Outlined.ArrowDownward
                    },
                    directionDescription = when {
                        !nameSelected -> null
                        sortOrder == AppSortOrder.NAME_ASCENDING -> stringResource(R.string.sort_ascending)
                        else -> stringResource(R.string.sort_descending)
                    },
                    onClick = {
                        onSortOrderChange(
                            if (nameSelected) {
                                if (sortOrder == AppSortOrder.NAME_ASCENDING) {
                                    AppSortOrder.NAME_DESCENDING
                                } else {
                                    AppSortOrder.NAME_ASCENDING
                                }
                            } else {
                                AppSortOrder.NAME_ASCENDING
                            }
                        )
                        sortMenuExpanded = false
                    }
                )

                val sizeSelected = sortOrder == AppSortOrder.SIZE_ASCENDING ||
                    sortOrder == AppSortOrder.SIZE_DESCENDING
                SortMenuItem(
                    text = stringResource(R.string.sort_by_size),
                    selected = sizeSelected,
                    directionIcon = when {
                        !sizeSelected -> null
                        sortOrder == AppSortOrder.SIZE_ASCENDING -> Icons.Outlined.ArrowUpward
                        else -> Icons.Outlined.ArrowDownward
                    },
                    directionDescription = when {
                        !sizeSelected -> null
                        sortOrder == AppSortOrder.SIZE_ASCENDING -> stringResource(R.string.sort_ascending)
                        else -> stringResource(R.string.sort_descending)
                    },
                    onClick = {
                        onSortOrderChange(
                            if (sizeSelected) {
                                if (sortOrder == AppSortOrder.SIZE_ASCENDING) {
                                    AppSortOrder.SIZE_DESCENDING
                                } else {
                                    AppSortOrder.SIZE_ASCENDING
                                }
                            } else {
                                AppSortOrder.SIZE_DESCENDING
                            }
                        )
                        sortMenuExpanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SortMenuItem(
    text: String,
    selected: Boolean,
    directionIcon: ImageVector?,
    directionDescription: String?,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(text) },
        modifier = Modifier.semantics { this.selected = selected },
        trailingIcon = directionIcon?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = directionDescription
                )
            }
        },
        onClick = onClick
    )
}
