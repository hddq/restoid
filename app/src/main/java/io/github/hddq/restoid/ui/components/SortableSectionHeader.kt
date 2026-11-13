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
    showSizeSort: Boolean = true,
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
                SortOrderMenuItem(
                    text = stringResource(R.string.sort_by_name),
                    ascending = AppSortOrder.NAME_ASCENDING,
                    descending = AppSortOrder.NAME_DESCENDING,
                    defaultDirection = AppSortOrder.NAME_ASCENDING,
                    sortOrder = sortOrder,
                    onSelect = onSortOrderChange,
                    onDismiss = { sortMenuExpanded = false }
                )

                if (showSizeSort) {
                    SortOrderMenuItem(
                        text = stringResource(R.string.sort_by_size),
                        ascending = AppSortOrder.SIZE_ASCENDING,
                        descending = AppSortOrder.SIZE_DESCENDING,
                        defaultDirection = AppSortOrder.SIZE_DESCENDING,
                        sortOrder = sortOrder,
                        onSelect = onSortOrderChange,
                        onDismiss = { sortMenuExpanded = false }
                    )
                }

                SortOrderMenuItem(
                    text = stringResource(R.string.sort_by_installation_date),
                    ascending = AppSortOrder.FIRST_INSTALL_TIME_ASCENDING,
                    descending = AppSortOrder.FIRST_INSTALL_TIME_DESCENDING,
                    defaultDirection = AppSortOrder.FIRST_INSTALL_TIME_DESCENDING,
                    sortOrder = sortOrder,
                    onSelect = onSortOrderChange,
                    onDismiss = { sortMenuExpanded = false }
                )
            }
        }
    }
}

@Composable
private fun SortOrderMenuItem(
    text: String,
    ascending: AppSortOrder,
    descending: AppSortOrder,
    defaultDirection: AppSortOrder,
    sortOrder: AppSortOrder,
    onSelect: (AppSortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    val isSelected = sortOrder == ascending || sortOrder == descending
    val directionIcon: ImageVector? = when {
        !isSelected -> null
        sortOrder == ascending -> Icons.Outlined.ArrowUpward
        else -> Icons.Outlined.ArrowDownward
    }
    val directionDescription: String? = when {
        !isSelected -> null
        sortOrder == ascending -> stringResource(R.string.sort_ascending)
        else -> stringResource(R.string.sort_descending)
    }

    DropdownMenuItem(
        text = { Text(text) },
        modifier = Modifier.semantics { this.selected = isSelected },
        trailingIcon = directionIcon?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = directionDescription
                )
            }
        },
        onClick = {
            onSelect(
                if (isSelected) {
                    if (sortOrder == ascending) descending else ascending
                } else {
                    defaultDirection
                }
            )
            onDismiss()
        }
    )
}
