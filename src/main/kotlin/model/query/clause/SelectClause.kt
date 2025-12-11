package model.query.clause

import model.query.select.SelectExpression
import model.query.select.toSql

data class SelectClause(
    val expression: SelectExpression,
    val alias: String? = null,
)

fun SelectClause.toSql(): String = buildString {
    append(expression.toSql())
    alias?.let { append(" AS $it") }
}