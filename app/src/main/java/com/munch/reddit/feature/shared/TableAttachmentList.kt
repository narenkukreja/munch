package com.munch.reddit.feature.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.munch.reddit.ui.theme.MaterialSpacing

@Composable
fun TableAttachmentList(
    tables: List<RedditHtmlTable>,
    modifier: Modifier = Modifier,
    onViewTable: (Int) -> Unit
) {
    if (tables.isEmpty()) return
    val spacing = MaterialSpacing
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        val headerText = if (tables.size == 1) "Contains a table" else "Contains tables"
        Text(
            text = headerText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        tables.forEachIndexed { index, table ->
            val preview = remember(table) { buildTablePreview(table) }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .clickable { onViewTable(index) }
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        Text(
                            text = table.displayName(index),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (preview.isNotEmpty()) {
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    AssistChip(
                        onClick = { onViewTable(index) },
                        label = { Text("View Table") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.TableChart,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }
        }
    }
}

private fun buildTablePreview(table: RedditHtmlTable): String {
    val headerPreview = table.header.filter { it.isNotBlank() }.take(4)
    if (headerPreview.isNotEmpty()) {
        return headerPreview.joinToString(" • ")
    }
    val firstRow = table.rows.firstOrNull { row -> row.any { it.isNotBlank() } } ?: return ""
    return firstRow.filter { it.isNotBlank() }.take(4).joinToString(" • ")
}
