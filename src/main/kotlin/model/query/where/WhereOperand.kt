package model.query.where

import model.query.ParsedQuery
import util.enums.AggregateFunctionType

sealed class WhereOperand {

    data class Column(
        val name: String,
        val table: String? = null
    ) : WhereOperand()

    data class Value(val sqlValue: String) : WhereOperand()

    data class ValueList(val values: List<String>) : WhereOperand()

    data class SubQuery(val query: ParsedQuery) : WhereOperand()

    data class Range(val from: String, val to: String) : WhereOperand()

    data class Function(
        val functionType: AggregateFunctionType,
        val column: String,
        val table: String? = null,
        val distinct: Boolean = false
    ) : WhereOperand()
}

fun WhereOperand.toSql(): String = when (this) {
    is WhereOperand.Column -> table?.let { "$it.$name" } ?: name
    is WhereOperand.Value -> sqlValue
    is WhereOperand.ValueList -> "(${values.joinToString(", ")})"
    is WhereOperand.SubQuery -> "(${query.toSql()})"
    is WhereOperand.Range -> "$from AND $to"
    is WhereOperand.Function -> {
        val distinctString = if (distinct) "DISTINCT " else ""
        val columnReference = table?.let { "$it.$column" } ?: column
        "${functionType.name}($distinctString$columnReference)"
    }
}