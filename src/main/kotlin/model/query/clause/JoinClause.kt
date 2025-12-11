package model.query.clause

import model.query.join.JoinCondition
import model.query.join.toSql
import util.enums.JoinType
import util.enums.toSqlKeyword

data class JoinClause(
    val joinType: JoinType,
    val source: SourceClause,
    val conditions: List<JoinCondition> = emptyList()
)

fun JoinClause.toSql(): String = buildString {
    append(joinType.toSqlKeyword())
    append(" ")
    append(source.toSql())

    if (conditions.isNotEmpty()) {
        append(" ON ")
        conditions.forEachIndexed { index, condition ->
            if (index > 0) {
                append(" ${conditions[index - 1].logicalOperator?.name ?: "AND"} ")
            }
            append(condition.toSql())
        }
    }
}