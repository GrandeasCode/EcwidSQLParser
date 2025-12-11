package model.query

import model.query.clause.GroupByClause
import model.query.clause.JoinClause
import model.query.clause.SelectClause
import model.query.clause.SortClause
import model.query.clause.SourceClause
import model.query.clause.WhereClause
import model.query.clause.toSql
import model.query.select.SelectExpression

data class ParsedQuery(
    val columns: List<SelectClause> = emptyList(),
    val from: List<SourceClause> = emptyList(),
    val joins: List<JoinClause> = emptyList(),
    val whereClauses: List<WhereClause> = emptyList(),
    val groupByColumns: List<GroupByClause> = emptyList(),
    val havingClauses: List<WhereClause> = emptyList(),
    val sortColumns: List<SortClause> = emptyList(),
    val limit: Int? = null,
    val offset: Int? = null,
) {

    fun toSql(): String = buildString {
        append("SELECT ")
        append(columns.joinToString(", ") { it.toSql() })

        if (from.isNotEmpty()) {
            append(" FROM ")
            append(from.joinToString(", ") { it.toSql() })
        }

        joins.forEach { join ->
            append(" ")
            append(join.toSql())
        }

        if (whereClauses.isNotEmpty()) {
            append(" WHERE ")
            append(whereClauses.toSql())
        }

        if (groupByColumns.isNotEmpty()) {
            append(" GROUP BY ")
            append(groupByColumns.joinToString(", ") { it.toSql() })
        }

        if (havingClauses.isNotEmpty()) {
            append(" HAVING ")
            append(havingClauses.toSql())
        }

        if (sortColumns.isNotEmpty()) {
            append(" ORDER BY ")
            append(sortColumns.joinToString(", ") { it.toSql() })
        }

        limit?.let { append(" LIMIT $it") }

        offset?.let { append(" OFFSET $it") }
    }

    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        if (columns.isEmpty()) {
            errors.add("SELECT clause is empty")
        }

        if (from.isEmpty() && columns.none { it.expression is SelectExpression.SubQuery }) {
            errors.add("FROM clause is required")
        }

        return errors
    }
}