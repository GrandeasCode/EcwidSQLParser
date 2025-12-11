package model.query.select

import model.query.ParsedQuery
import util.enums.AggregateFunctionType

sealed class SelectExpression {

    data class AllColumns(
        val table: String? = null
    ) : SelectExpression()

    data class Column(
        val name: String,
        val table: String? = null
    ) : SelectExpression()

    data class Function(
        val functionType: AggregateFunctionType,
        val argument: SelectExpression,
        val distinct: Boolean = false
    ) : SelectExpression()

    data class SubQuery(
        val query: ParsedQuery
    ) : SelectExpression()
}

fun SelectExpression.toSql(): String = when (this) {
    is SelectExpression.AllColumns -> table?.let { "$it.*" } ?: "*"
    is SelectExpression.Column -> table?.let { "$it.$name" } ?: name
    is SelectExpression.Function -> {
        val distinctStr = if (distinct) "DISTINCT " else ""
        "${functionType.name}($distinctStr${argument.toSql()})"
    }
    is SelectExpression.SubQuery -> "(${query.toSql()})"
}
