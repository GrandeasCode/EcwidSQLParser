package service.clause

import model.query.ParsedQuery
import model.query.clause.SelectClause
import model.query.select.SelectExpression
import util.ColumnParser
import util.ParsingUtil
import util.SqlKeywords.ALL

open class SelectClauseParser(
    private val parsingUtil: ParsingUtil = ParsingUtil(),
    private val subQueryParser: ((String) -> ParsedQuery)? = null
) {
    open fun parseSelect(selectClause: String): List<SelectClause> =
        parsingUtil.splitByComma(selectClause).map { parseSelectItem(it.trim()) }

    private fun parseSelectItem(item: String): SelectClause {
        val (expression, alias) = parsingUtil.extractAlias(item)
        return SelectClause(
            expression = parseSelectExpression(expression),
            alias = alias
        )
    }

    private fun parseSelectExpression(expression: String): SelectExpression {
        val trimmed = expression.trim()

        return when {
            trimmed == ALL -> SelectExpression.AllColumns(null)
            trimmed.endsWith(".$ALL") -> SelectExpression.AllColumns(trimmed.dropLast(2))
            parsingUtil.isSubQuery(trimmed) -> parseSubQueryExpression(trimmed)
            parsingUtil.isAggregateFunction(trimmed) -> parseAggregateFunction(trimmed)
            else -> parseColumnExpression(trimmed)
        }
    }

    private fun parseSubQueryExpression(expression: String): SelectExpression.SubQuery {
        val innerSqlText = expression.drop(1).dropLast(1).trim()
        val parser = subQueryParser ?: error("SubQuery parser not provided")
        return SelectExpression.SubQuery(parser(innerSqlText))
    }

    private fun parseColumnExpression(expression: String): SelectExpression.Column {
        val parsed = ColumnParser.parse(expression)
        return SelectExpression.Column(name = parsed.name, table = parsed.table)
    }

    private fun parseAggregateFunction(expression: String): SelectExpression.Function {
        val parsed = parsingUtil.parseAggregateFunction(expression)
        return SelectExpression.Function(
            functionType = parsed.functionType,
            argument = parseSelectExpression(parsed.argument),
            distinct = parsed.distinct
        )
    }
}