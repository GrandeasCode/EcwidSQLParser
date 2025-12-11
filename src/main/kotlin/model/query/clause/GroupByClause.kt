package model.query.clause

data class GroupByClause(
    val column: String,
    val table: String? = null
)

fun GroupByClause.toSql(): String = table?.let { "$it.$column" } ?: column
