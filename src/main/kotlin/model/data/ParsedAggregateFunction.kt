package model.data

import util.enums.AggregateFunctionType

data class ParsedAggregateFunction(
    val functionType: AggregateFunctionType,
    val argument: String,
    val distinct: Boolean
)