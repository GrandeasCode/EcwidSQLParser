package model.data

import util.enums.LogicalOperator

data class ExpressionWithOperator(
    val expression: String,
    val followingOperator: LogicalOperator?
)