package com.munch.reddit.feature.shared

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.Serializable

enum class TableCellAlignment : Serializable {
    START, CENTER, END
}

data class RedditHtmlTable(
    val title: String?,
    val header: List<String>,
    val rows: List<List<String>>,
    val alignments: List<TableCellAlignment>
) : Serializable {
    val columnCount: Int = alignments.size
    val nonBlankCellCount: Int = header.count { it.isNotBlank() } + rows.sumOf { row ->
        row.count { it.isNotBlank() }
    }

    fun displayName(index: Int): String {
        val sanitizedTitle = title?.trim().orEmpty()
        return if (sanitizedTitle.isNotEmpty()) sanitizedTitle else "Table ${index + 1}"
    }
}

fun parseTablesFromHtml(rawHtml: String?): List<RedditHtmlTable> {
    if (rawHtml.isNullOrBlank()) return emptyList()
    val document = Jsoup.parseBodyFragment(rawHtml)
    val tables = document.select("table")
    if (tables.isEmpty()) return emptyList()

    return tables.mapNotNull { element ->
        element.toRedditHtmlTable()
    }.filter { table ->
        table.columnCount >= 2 && table.rows.isNotEmpty() && table.nonBlankCellCount >= table.columnCount * 2
    }
}

fun stripTablesFromHtml(rawHtml: String?): String? {
    if (rawHtml.isNullOrBlank()) return null
    val document = Jsoup.parseBodyFragment(rawHtml)
    val tables = document.select("table")
    if (tables.isEmpty()) return rawHtml
    tables.forEach { it.remove() }
    return document.body().html().trim()
}

private fun Element.toRedditHtmlTable(): RedditHtmlTable? {
    val headerRow = selectFirst("thead tr") ?: select("tr").firstOrNull { row ->
        row.select("th").isNotEmpty()
    }

    val allRows = select("tbody tr").ifEmpty { select("tr") }
    val headerCells = headerRow?.extractCells() ?: emptyList()
    val dataRows = allRows
        .filter { it != headerRow }
        .mapNotNull { row ->
            val cells = row.extractCells()
            if (cells.isEmpty() || cells.all { it.text.isBlank() }) null else cells
        }

    val columnCount = maxOf(
        headerCells.size,
        dataRows.maxOfOrNull { it.size } ?: 0
    )
    if (columnCount == 0) return null

    val alignments = (0 until columnCount).map { columnIndex ->
        headerCells.getOrNull(columnIndex)?.alignment
            ?: dataRows.firstNotNullOfOrNull { row -> row.getOrNull(columnIndex)?.alignment }
            ?: TableCellAlignment.START
    }

    val normalizedHeader = headerCells.normalize(columnCount)
    val normalizedRows = dataRows.map { row -> row.normalize(columnCount) }
        .filter { normalizedRow -> normalizedRow.any { it.isNotBlank() } }

    if (normalizedRows.isEmpty()) return null

    val titleCandidate = findTitleCandidate()

    return RedditHtmlTable(
        title = titleCandidate,
        header = normalizedHeader,
        rows = normalizedRows,
        alignments = alignments
    )
}

private data class ParsedCell(
    val text: String,
    val alignment: TableCellAlignment
)

private fun Element.extractCells(): List<ParsedCell> {
    return select("th, td").map { cell ->
        val cleanedText = cell.text()
            .replace("\u00a0", " ")
            .trim()
        val alignment = cell.resolveAlignment()
        ParsedCell(cleanedText, alignment)
    }
}

private fun Element.resolveAlignment(): TableCellAlignment {
    val alignAttr = attr("align").lowercase()
    val styleAttr = attr("style").lowercase()
    return when {
        "center" in alignAttr || "text-align:center" in styleAttr -> TableCellAlignment.CENTER
        "right" in alignAttr || "text-align:right" in styleAttr -> TableCellAlignment.END
        "middle" in alignAttr -> TableCellAlignment.CENTER
        else -> TableCellAlignment.START
    }
}

private fun List<ParsedCell>.normalize(targetColumns: Int): List<String> {
    if (isEmpty() && targetColumns == 0) return emptyList()
    val normalized = ArrayList<String>(targetColumns)
    repeat(targetColumns) { index ->
        val value = getOrNull(index)?.text.orEmpty()
        normalized += value
    }
    return normalized
}

private fun Element.findTitleCandidate(): String? {
    var current: Element? = previousElementSibling()
    repeat(4) {
        if (current == null) return null
        val text = current!!.text().replace("\u00a0", " ").trim()
        if (text.isNotEmpty()) return text
        current = current!!.previousElementSibling()
    }
    return null
}
