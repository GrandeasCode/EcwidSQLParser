package util

import util.SqlChars.DOT

object ColumnParser {

    data class ParsedColumn(
        val name: String,
        val table: String? = null
    )

    fun parse(expression: String): ParsedColumn {
        val trimmed = expression.trim()
        val parts = trimmed.split(DOT)

        return when (parts.size) {
            1 -> ParsedColumn(name = parts[0])
            2 -> ParsedColumn(name = parts[1], table = parts[0])
            else -> ParsedColumn(name = trimmed)
        }
    }
}