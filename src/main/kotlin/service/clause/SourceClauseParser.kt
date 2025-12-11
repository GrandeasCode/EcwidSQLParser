package service.clause

import model.query.ParsedQuery
import model.query.clause.SourceClause
import util.ParsingUtil

open class SourceClauseParser(
    private val parsingUtil: ParsingUtil = ParsingUtil(),
    private val subQueryParser: ((String) -> ParsedQuery)? = null
) {

    open fun parseFrom(fromClause: String): List<SourceClause> =
        parsingUtil.splitByComma(fromClause).map { parseSourceClause(it.trim()) }

    open fun parseSourceClause(item: String): SourceClause {
        val (expression, alias) = parsingUtil.extractAlias(item)
        val trimmed = expression.trim()

        return if (parsingUtil.isSubQuery(trimmed)) {
            parseSubQuerySource(trimmed, alias)
        } else {
            parseTableSource(trimmed, alias)
        }
    }

    private fun parseSubQuerySource(expression: String, alias: String?): SourceClause.SubQuery {
        val innerSqlText = expression.drop(1).dropLast(1).trim()
        val parser = subQueryParser ?: error("SubQuery parser not provided")
        return SourceClause.SubQuery(query = parser(innerSqlText), alias = alias)
    }

    private fun parseTableSource(expression: String, alias: String?): SourceClause.Table {
        val parts = expression.split(".")
        return when (parts.size) {
            1 -> SourceClause.Table(name = parts[0], alias = alias)
            2 -> SourceClause.Table(name = parts[1], schema = parts[0], alias = alias)
            else -> SourceClause.Table(name = expression, alias = alias)
        }
    }
}