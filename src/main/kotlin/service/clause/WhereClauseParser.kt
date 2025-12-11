package service.clause

import model.query.ParsedQuery
import model.query.clause.WhereClause
import model.query.where.WhereOperand
import util.ColumnParser
import util.ParsingUtil
import util.SqlKeywords.AND
import util.SqlKeywords.BETWEEN
import util.SqlKeywords.IN
import util.SqlKeywords.IS_NOT_NULL
import util.SqlKeywords.IS_NULL
import util.SqlKeywords.LIKE
import util.SqlKeywords.NOT
import util.SqlKeywords.NOT_IN
import util.SqlKeywords.NOT_LIKE
import util.SqlKeywords.SELECT
import util.enums.ComparisonOperator
import kotlin.text.RegexOption.IGNORE_CASE

open class WhereClauseParser(
    private val parsingUtil: ParsingUtil = ParsingUtil(),
    private val subQueryParser: ((String) -> ParsedQuery)? = null
) {

    open fun parseWhere(whereClause: String): List<WhereClause> {
        val parts = parsingUtil.splitByLogicalOperators(whereClause)
        return parts.mapIndexed { index, part ->
            val clause = parseWhereCondition(part.expression.trim())
            val hasNext = index < parts.size - 1
            clause.copy(logicalOperator = if (hasNext) part.followingOperator else null)
        }
    }

    private fun parseWhereCondition(conditionString: String): WhereClause {
        var expression = conditionString.trim()

        val isNegated = expression.uppercase().startsWith("$NOT ")
        if (isNegated) {
            expression = expression.substring(NOT.length + 1).trim()
        }

        return parseSpecialOperator(expression, isNegated)
            ?: parseStandardOperator(expression, isNegated)
    }

    private fun parseSpecialOperator(expression: String, isNegated: Boolean): WhereClause? {
        val uppercaseExpression = expression.uppercase()

        if (uppercaseExpression.contains(" $IS_NOT_NULL")) {
            return createNullClause(expression, IS_NOT_NULL, ComparisonOperator.IS_NOT_NULL, isNegated)
        }

        if (uppercaseExpression.contains(" $IS_NULL")) {
            return createNullClause(expression, IS_NULL, ComparisonOperator.IS_NULL, isNegated)
        }

        Regex("(.+?)\\s+$BETWEEN\\s+(.+?)\\s+$AND\\s+(.+)", IGNORE_CASE)
            .find(expression)?.let { match ->
                val (left, from, to) = match.destructured
                return WhereClause(
                    leftOperand = parseWhereOperand(left.trim()),
                    operator = ComparisonOperator.BETWEEN,
                    rightOperand = WhereOperand.Range(from.trim(), to.trim()),
                    isNegated = isNegated
                )
            }

        Regex("(.+?)\\s+${NOT_IN}\\s*\\((.+)\\)", IGNORE_CASE)
            .find(expression)?.let { match ->
                return createInClause(match, ComparisonOperator.NOT_IN, isNegated)
            }

        Regex("(.+?)\\s+$IN\\s*\\((.+)\\)", IGNORE_CASE)
            .find(expression)?.let { match ->
                return createInClause(match, ComparisonOperator.IN, isNegated)
            }

        Regex("(.+?)\\s+$NOT_LIKE\\s+(.+)", IGNORE_CASE)
            .find(expression)?.let { match ->
                return createLikeClause(match, ComparisonOperator.NOT_LIKE, isNegated)
            }

        Regex("(.+?)\\s+$LIKE\\s+(.+)", IGNORE_CASE)
            .find(expression)?.let { match ->
                return createLikeClause(match, ComparisonOperator.LIKE, isNegated)
            }

        return null
    }

    private fun createNullClause(
        expression: String,
        keyword: String,
        operator: ComparisonOperator,
        isNegated: Boolean
    ): WhereClause {
        val column = expression.substringBefore(" $keyword", "").trim()
        return WhereClause(
            leftOperand = parseWhereOperand(column),
            operator = operator,
            rightOperand = null,
            isNegated = isNegated
        )
    }

    private fun createInClause(match: MatchResult, operator: ComparisonOperator, isNegated: Boolean): WhereClause {
        val (left, values) = match.destructured
        return WhereClause(
            leftOperand = parseWhereOperand(left.trim()),
            operator = operator,
            rightOperand = parseInValues(values.trim()),
            isNegated = isNegated
        )
    }

    private fun createLikeClause(match: MatchResult, operator: ComparisonOperator, isNegated: Boolean): WhereClause {
        val (left, right) = match.destructured
        return WhereClause(
            leftOperand = parseWhereOperand(left.trim()),
            operator = operator,
            rightOperand = WhereOperand.Value(right.trim()),
            isNegated = isNegated
        )
    }

    private fun parseStandardOperator(expression: String, isNegated: Boolean): WhereClause {
        val operator = parsingUtil.findComparisonOperator(expression)
        val (leftString, rightString) = expression.split(operator.symbol, limit = 2)
            .let { it[0].trim() to it.getOrElse(1) { "" }.trim() }

        return WhereClause(
            leftOperand = parseWhereOperand(leftString),
            operator = operator,
            rightOperand = WhereOperand.Value(rightString),
            isNegated = isNegated
        )
    }

    private fun parseInValues(valuesString: String): WhereOperand {
        val trimmed = valuesString.trim()
        return if (trimmed.uppercase().startsWith(SELECT)) {
            val parser = subQueryParser ?: error("SubQuery parser not provided")
            WhereOperand.SubQuery(parser(trimmed))
        } else {
            WhereOperand.ValueList(parsingUtil.splitByComma(trimmed).map { it.trim() })
        }
    }

    private fun parseWhereOperand(expression: String): WhereOperand {
        val trimmed = expression.trim()

        return when {
            parsingUtil.isSubQuery(trimmed) -> {
                val innerSqlText = trimmed.drop(1).dropLast(1).trim()
                val parser = subQueryParser ?: error("SubQuery parser not provided")
                WhereOperand.SubQuery(parser(innerSqlText))
            }
            parsingUtil.isAggregateFunction(trimmed) -> parseWhereAggregateFunction(trimmed)
            parsingUtil.isColumnReference(trimmed) -> {
                val parsed = ColumnParser.parse(trimmed)
                WhereOperand.Column(name = parsed.name, table = parsed.table)
            }
            else -> WhereOperand.Value(trimmed)
        }
    }

    private fun parseWhereAggregateFunction(expression: String): WhereOperand.Function {
        val parsed = parsingUtil.parseAggregateFunction(expression)
        val column = ColumnParser.parse(parsed.argument)

        return WhereOperand.Function(
            functionType = parsed.functionType,
            column = column.name,
            table = column.table,
            distinct = parsed.distinct
        )
    }
}