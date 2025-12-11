package model.query.join

data class ColumnReference(
    val column: String,
    val table: String? = null
)

fun ColumnReference.toSql(): String = table?.let { "$it.$column" } ?: column
