package service.clause

import model.query.clause.JoinClause
import model.query.clause.SourceClause
import model.query.join.ColumnReference
import model.query.join.JoinCondition
import model.query.join.JoinOperand
import util.ColumnParser
import util.ParsingUtil
import util.SqlKeywords.JOIN_PATTERN
import util.SqlKeywords.ON
import util.enums.JoinType
import util.enums.JoinType.CROSS
import util.enums.JoinType.FULL
import util.enums.JoinType.INNER
import util.enums.JoinType.LEFT
import util.enums.JoinType.RIGHT

open class JoinClauseParser(
    private val parsingUtil: ParsingUtil = ParsingUtil(),
    private val sourceClauseParser: ((String) -> SourceClause)? = null
) {

    open fun parseJoins(joinClause: String): List<JoinClause> {
        val matches = JOIN_PATTERN.findAll(joinClause).toList()

        return matches.mapIndexed { index, match ->
            val joinType = parseJoinType(match.value)
            val startIndex = match.range.last + 1
            val endIndex = matches.getOrNull(index + 1)?.range?.first ?: joinClause.length
            val joinContent = joinClause.substring(startIndex, endIndex).trim()

            parseJoinClause(joinType, joinContent)
        }
    }

    private fun parseJoinType(typeString: String): JoinType {
        val normalized = typeString.uppercase().replace(Regex("\\s+"), " ")
        return when {
            "LEFT" in normalized -> LEFT
            "RIGHT" in normalized -> RIGHT
            "FULL" in normalized -> FULL
            "CROSS" in normalized -> CROSS
            else -> INNER
        }
    }

    private fun parseJoinClause(joinType: JoinType, content: String): JoinClause {
        val onIndex = parsingUtil.findKeywordIndex(content, ON)

        val (sourceString, conditionsString) = if (onIndex != -1) {
            content.take(onIndex).trim() to content.substring(onIndex + ON.length).trim()
        } else {
            content.trim() to null
        }

        val parser = sourceClauseParser ?: error("SourceClause parser not provided")

        return JoinClause(
            joinType = joinType,
            source = parser(sourceString),
            conditions = conditionsString?.let { parseJoinConditions(it) } ?: emptyList()
        )
    }

    private fun parseJoinConditions(conditionsString: String): List<JoinCondition> {
        val parts = parsingUtil.splitByLogicalOperators(conditionsString)
        return parts.mapIndexed { index, part ->
            val condition = parseJoinCondition(part.expression.trim())
            val hasNext = index < parts.size - 1
            condition.copy(logicalOperator = if (hasNext) part.followingOperator else null)
        }
    }

    private fun parseJoinCondition(conditionString: String): JoinCondition {
        val operator = parsingUtil.findComparisonOperator(conditionString)
        val (leftString, rightString) = conditionString.split(operator.symbol, limit = 2)
            .let { it[0].trim() to it.getOrElse(1) { "" }.trim() }

        return JoinCondition(
            leftOperand = parseColumnReference(leftString),
            operator = operator,
            rightOperand = parseJoinOperand(rightString)
        )
    }

    private fun parseColumnReference(expression: String): ColumnReference {
        val parsed = ColumnParser.parse(expression)
        return ColumnReference(column = parsed.name, table = parsed.table)
    }

    private fun parseJoinOperand(expression: String): JoinOperand {
        val trimmed = expression.trim()

        return if (parsingUtil.isColumnReference(trimmed)) {
            val parsed = ColumnParser.parse(trimmed)
            JoinOperand.Column(name = parsed.name, table = parsed.table)
        } else {
            JoinOperand.Value(trimmed)
        }
    }
}