package model.query

data class SplitSqlQuery(
    val select: String,
    val from: String,
    val join: String? = null,
    val where: String? = null,
    val groups: String? = null,
    val having: String? = null,
    val sort: String? = null,
    val limit: Int? = null,
    val offset: Int? = null
)