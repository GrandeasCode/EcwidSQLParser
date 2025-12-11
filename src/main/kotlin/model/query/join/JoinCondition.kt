package model.query.join

import util.enums.ComparisonOperator
import util.enums.LogicalOperator

data class JoinCondition(
    val leftOperand: ColumnReference,
    val operator: ComparisonOperator,
    val rightOperand: JoinOperand,
    val logicalOperator: LogicalOperator? = null
)

fun JoinCondition.toSql(): String = buildString {
    append(leftOperand.toSql())
    append(" ${operator.symbol} ")
    append(rightOperand.toSql())
}
