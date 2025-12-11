package util.enums

enum class JoinType {
    INNER,
    LEFT,
    RIGHT,
    FULL,
    CROSS
}

fun JoinType.toSqlKeyword(): String = when (this) {
    JoinType.INNER -> "INNER JOIN"
    JoinType.LEFT -> "LEFT JOIN"
    JoinType.RIGHT -> "RIGHT JOIN"
    JoinType.FULL -> "FULL JOIN"
    JoinType.CROSS -> "CROSS JOIN"
}
