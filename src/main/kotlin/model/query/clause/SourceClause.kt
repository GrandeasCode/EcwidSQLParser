package model.query.clause

import model.query.ParsedQuery

sealed class SourceClause {
    abstract val alias: String?

    data class Table(
        val name: String,
        val schema: String? = null,
        override val alias: String? = null
    ) : SourceClause()

    data class SubQuery(
        val query: ParsedQuery,
        override val alias: String?
    ) : SourceClause()
}

fun SourceClause.toSql(): String = when (this) {
    is SourceClause.Table -> {
        val schemaPrefix = schema?.let { "$it." } ?: ""
        val aliasStr = alias?.let { " AS $it" } ?: ""
        "$schemaPrefix$name$aliasStr"
    }
    is SourceClause.SubQuery -> {
        val aliasStr = alias?.let { " AS $it" } ?: ""
        "(${query.toSql()})$aliasStr"
    }
}