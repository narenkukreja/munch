package com.munch.reddit.feature.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.munch.reddit.ui.theme.MaterialSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableViewerScreen(
    postTitle: String,
    tables: List<RedditHtmlTable>,
    startIndex: Int = 0,
    onBackClick: () -> Unit
) {
    val spacing = MaterialSpacing
    val safeIndex = startIndex.coerceIn(0, tables.lastIndex)
    var selectedIndex by rememberSaveable(postTitle, tables.size) {
        mutableStateOf(safeIndex)
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Tables",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            if (postTitle.isNotBlank()) {
                Text(
                    text = postTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (tables.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    edgePadding = spacing.sm
                ) {
                    tables.forEachIndexed { index, table ->
                        Tab(
                            selected = index == selectedIndex,
                            onClick = { selectedIndex = index },
                            text = { Text(table.displayName(index)) }
                        )
                    }
                }
            }
            if (tables.isNotEmpty()) {
                val currentIndex = selectedIndex.coerceIn(0, tables.lastIndex)
                val currentTable = tables[currentIndex]
                TableCard(
                    table = currentTable,
                    displayLabel = currentTable.displayName(currentIndex)
                )
            }
        }
    }
}

@Composable
private fun TableCard(
    table: RedditHtmlTable,
    displayLabel: String,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialSpacing
    val horizontalScroll = rememberScrollState()
    val columnWidths = rememberColumnWidths(table)
    if (columnWidths.isEmpty()) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Text(
                text = table.title?.takeIf { it.isNotBlank() } ?: displayLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Box(
                modifier = Modifier
                    .horizontalScroll(horizontalScroll)
                    .fillMaxWidth()
            ) {
                Column {
                    if (table.header.any { it.isNotBlank() }) {
                        TableRowView(
                            cells = table.header,
                            columnWidths = columnWidths,
                            alignments = table.alignments,
                            isHeader = true,
                            backgroundColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        )
                    }
                    table.rows.forEachIndexed { index, row ->
                        val backgroundColor = if (index % 2 == 0) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        } else {
                            Color.Transparent
                        }
                        TableRowView(
                            cells = row,
                            columnWidths = columnWidths,
                            alignments = table.alignments,
                            isHeader = false,
                            backgroundColor = backgroundColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberColumnWidths(table: RedditHtmlTable): List<Dp> {
    return remember(table) {
        if (table.columnCount == 0) return@remember emptyList<Dp>()
        val baseWidth = 72.dp
        val extraUnit = 4.dp
        val maxWidth = 220.dp
        val charCounts = IntArray(table.columnCount) { 1 }
        val allRows = buildList {
            if (table.header.isNotEmpty()) add(table.header)
            addAll(table.rows)
        }
        allRows.forEach { row ->
            row.forEachIndexed { index, cell ->
                val length = cell.length.coerceAtLeast(1)
                if (length > charCounts[index]) {
                    charCounts[index] = length.coerceAtMost(48)
                }
            }
        }
        val widths = ArrayList<Dp>(table.columnCount)
        charCounts.forEach { count ->
            val width = (baseWidth + extraUnit * (count - 1)).coerceAtMost(maxWidth)
            widths += width
        }
        widths
    }
}

@Composable
private fun TableRowView(
    cells: List<String>,
    columnWidths: List<Dp>,
    alignments: List<TableCellAlignment>,
    isHeader: Boolean,
    backgroundColor: Color
) {
    val spacing = MaterialSpacing
    val totalWidth = columnWidths.fold(0.dp) { acc, width -> acc + width }
    Row(
        modifier = Modifier
            .width(totalWidth)
            .background(backgroundColor),
        horizontalArrangement = Arrangement.Start
    ) {
        columnWidths.forEachIndexed { index, width ->
            val text = cells.getOrNull(index).orEmpty().ifBlank { "\u00A0" }
            val alignment = alignments.getOrNull(index) ?: TableCellAlignment.START
            Text(
                text = text,
                modifier = Modifier
                    .width(width)
                    .padding(horizontal = spacing.sm, vertical = spacing.sm),
                textAlign = alignment.toTextAlign(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

private fun TableCellAlignment.toTextAlign(): TextAlign {
    return when (this) {
        TableCellAlignment.START -> TextAlign.Start
        TableCellAlignment.CENTER -> TextAlign.Center
        TableCellAlignment.END -> TextAlign.End
    }
}
