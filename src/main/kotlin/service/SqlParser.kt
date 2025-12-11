package service

import model.query.ParsedQuery
import model.query.SplitSqlQuery
import model.query.clause.GroupByClause
import model.query.clause.SortClause
import service.clause.JoinClauseParser
import service.clause.SelectClauseParser
import service.clause.SourceClauseParser
import service.clause.WhereClauseParser
import util.ParsingUtil
import util.SqlChars.CLOSE_PARENTHESES
import util.SqlChars.DOT
import util.SqlChars.OPEN_PARENTHESES
import util.SqlKeywords.DESC
import util.SqlKeywords.FIRST
import util.SqlKeywords.LAST
import util.enums.NullsPosition
import util.enums.SortDirection

class SqlParser(
    private val parsingUtil: ParsingUtil = ParsingUtil()
) {
    private val selectClauseParser = SelectClauseParser(parsingUtil) { parseSubQuery(it) }
    private val sourceClauseParser = SourceClauseParser(parsingUtil) { parseSubQuery(it) }
    private val joinClauseParser = JoinClauseParser(parsingUtil) { sourceClauseParser.parseSourceClause(it) }
    private val whereClauseParser = WhereClauseParser(parsingUtil) { parseSubQuery(it) }

    fun parseQuery(splitSqlQuery: SplitSqlQuery): ParsedQuery =
        ParsedQuery(
            columns = selectClauseParser.parseSelect(splitSqlQuery.select),
            from = sourceClauseParser.parseFrom(splitSqlQuery.from),
            joins = splitSqlQuery.join?.let { joinClauseParser.parseJoins(it) } ?: emptyList(),
            whereClauses = splitSqlQuery.where?.let { whereClauseParser.parseWhere(it) } ?: emptyList(),
            groupByColumns = splitSqlQuery.groups?.let { parseGroupBy(it) } ?: emptyList(),
            havingClauses = splitSqlQuery.having?.let { whereClauseParser.parseWhere(it) } ?: emptyList(),
            sortColumns = splitSqlQuery.sort?.let { parseOrderBy(it) } ?: emptyList(),
            limit = splitSqlQuery.limit,
            offset = splitSqlQuery.offset,
        )

    private fun parseGroupBy(groupByClause: String): List<GroupByClause> =
        parsingUtil.splitByComma(groupByClause).map { item ->
            val trimmed = item.trim()
            val parts = trimmed.split(DOT)

            when (parts.size) {
                1 -> GroupByClause(column = parts[0])
                2 -> GroupByClause(column = parts[1], table = parts[0])
                else -> GroupByClause(column = trimmed)
            }
        }

    private fun parseOrderBy(orderByClause: String): List<SortClause> =
        parsingUtil.splitByComma(orderByClause).map { item ->
            parseOrderByItem(item.trim())
        }

    private fun parseOrderByItem(item: String): SortClause {
        val trimmed = item.trim()

        if (parsingUtil.isSubQuery(trimmed) || trimmed.startsWith(OPEN_PARENTHESES)) {
            return parseOrderByWithSubQuery(trimmed)
        }

        val parts = trimmed.split(Regex("\\s+"))
        val columnPart = parts[0]

        val direction = when {
            parts.any { it.uppercase() == DESC } -> SortDirection.DESC
            else -> SortDirection.ASC
        }
        val nullsPosition = when {
            parts.any { it.uppercase() == FIRST } -> NullsPosition.FIRST
            parts.any { it.uppercase() == LAST } -> NullsPosition.LAST
            else -> null
        }

        val columnParts = columnPart.split(DOT)
        return when (columnParts.size) {
            1 -> SortClause(column = columnParts[0], direction = direction, nullsPosition = nullsPosition)
            2 -> SortClause(column = columnParts[1], table = columnParts[0], direction = direction, nullsPosition = nullsPosition)
            else -> SortClause(column = columnPart, direction = direction, nullsPosition = nullsPosition)
        }
    }

    private fun parseOrderByWithSubQuery(item: String): SortClause {
        var depth = 0
        var subQueryEnd = 0

        for (index in item.indices) {
            when (item[index]) {
                OPEN_PARENTHESES -> depth++
                CLOSE_PARENTHESES -> {
                    depth--
                    if (depth == 0) {
                        subQueryEnd = index
                        break
                    }
                }
            }
        }

        val subQueryPart = item.take(subQueryEnd + 1)
        val remaining = item.substring(subQueryEnd + 1).trim()

        val direction = when {
            remaining.uppercase().contains(DESC) -> SortDirection.DESC
            else -> SortDirection.ASC
        }
        val nullsPosition = when {
            remaining.uppercase().contains(FIRST) -> NullsPosition.FIRST
            remaining.uppercase().contains(LAST) -> NullsPosition.LAST
            else -> null
        }

        return SortClause(
            column = subQueryPart,
            direction = direction,
            nullsPosition = nullsPosition
        )
    }

    private fun parseSubQuery(sqlText: String): ParsedQuery {
        val splitter = SqlSplitter()
        val splitQuery = splitter.formSplitSqlQuery(sqlText)
        return parseQuery(splitQuery)
    }

    fun validateParsedQuery(query: ParsedQuery): List<String> {
        val errors = mutableListOf<String>()

        if (query.columns.isEmpty()) {
            errors.add("SELECT clause is empty")
        }

        if (query.from.isEmpty()) {
            errors.add("FROM clause is empty")
        }

        return errors
    }
}