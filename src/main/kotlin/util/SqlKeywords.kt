package util

object SqlKeywords {
    const val SELECT = "SELECT"
    const val DISTINCT = "DISTINCT"
    const val AS = "AS"
    const val ALL = "*"

    const val FROM = "FROM"

    val JOIN_TYPES = listOf(
        "LEFT OUTER JOIN",
        "RIGHT OUTER JOIN",
        "FULL OUTER JOIN",
        "INNER JOIN",
        "LEFT JOIN",
        "RIGHT JOIN",
        "FULL JOIN",
        "CROSS JOIN",
        "JOIN"
    )

    val JOIN_KEYWORDS_SET = setOf(
        "JOIN", "INNER JOIN", "LEFT JOIN", "RIGHT JOIN", "FULL JOIN", "CROSS JOIN",
        "LEFT OUTER JOIN", "RIGHT OUTER JOIN", "FULL OUTER JOIN"
    )

    val JOIN_PATTERN: Regex by lazy {
        val pattern = JOIN_TYPES
            .sortedByDescending { it.length }
            .joinToString("|") { it.replace(" ", "\\s+") }
        Regex("($pattern)", RegexOption.IGNORE_CASE)
    }

    const val ON = "ON"

    const val WHERE = "WHERE"
    const val AND = "AND"
    const val OR = "OR"
    const val NOT = "NOT"
    const val IN = "IN"
    const val NOT_IN = "NOT IN"
    const val LIKE = "LIKE"
    const val NOT_LIKE = "NOT LIKE"
    const val BETWEEN = "BETWEEN"
    const val IS_NULL = "IS NULL"
    const val IS_NOT_NULL = "IS NOT NULL"

    const val GROUP_BY = "GROUP BY"
    const val HAVING = "HAVING"

    const val ORDER_BY_TEXT = "ORDER BY"
    const val ASC = "ASC"
    const val DESC = "DESC"
    const val NULLS = "NULLS"
    const val FIRST = "FIRST"
    const val LAST = "LAST"

    const val LIMIT = "LIMIT"
    const val OFFSET = "OFFSET"

    val MAIN_KEYWORDS = listOf(
        "LEFT OUTER JOIN",
        "RIGHT OUTER JOIN",
        "FULL OUTER JOIN",
        "INNER JOIN",
        "LEFT JOIN",
        "RIGHT JOIN",
        "FULL JOIN",
        "CROSS JOIN",
        GROUP_BY,
        ORDER_BY_TEXT,
        SELECT,
        FROM,
        "JOIN",
        WHERE,
        HAVING,
        LIMIT,
        OFFSET
    )
}