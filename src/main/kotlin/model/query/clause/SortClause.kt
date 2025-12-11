package model.query.clause

import util.enums.NullsPosition
import util.enums.SortDirection
import util.enums.SortDirection.ASC

data class SortClause(
    val column: String,
    val table: String? = null,
    val direction: SortDirection = ASC,
    val nullsPosition: NullsPosition? = null
)

fun SortClause.toSql(): String = buildString {
    append(table?.let { "$it.$column" } ?: column)
    append(" ${direction.name}")
    nullsPosition?.let { append(" NULLS ${it.name}") }
}
