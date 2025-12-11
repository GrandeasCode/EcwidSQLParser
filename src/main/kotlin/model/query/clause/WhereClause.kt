package model.query.clause

import model.query.where.WhereOperand
import model.query.where.toSql
import util.enums.ComparisonOperator
import util.enums.LogicalOperator

data class WhereClause(
    val leftOperand: WhereOperand,
    val operator: ComparisonOperator,
    val rightOperand: WhereOperand? = null,
    val logicalOperator: LogicalOperator? = null,
    val isNegated: Boolean = false
)

fun WhereClause.toSql(): String = buildString {
    if (isNegated) append("NOT ")
    append(leftOperand.toSql())
    append(" ${operator.symbol}")

    rightOperand?.let { right ->
        when (operator) {
            ComparisonOperator.BETWEEN -> {
                val range = right as WhereOperand.Range
                append(" ${range.from} AND ${range.to}")
            }
            ComparisonOperator.IS_NULL, ComparisonOperator.IS_NOT_NULL -> {}
            else -> append(" ${right.toSql()}")
        }
    }
}

fun List<WhereClause>.toSql(): String {
    if (isEmpty()) return ""

    return mapIndexed { index, clause ->
        if (index > 0) {
            val prevLogicalOp = this[index - 1].logicalOperator?.name ?: "AND"
            "$prevLogicalOp ${clause.toSql()}"
        } else {
            clause.toSql()
        }
    }.joinToString(" ")
}