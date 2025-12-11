package util.enums


enum class ComparisonOperator(val symbol: String) {
    EQUALS("="),
    NOT_EQUALS("!="),
    GREATER_THAN(">"),
    LESS_THAN("<"),
    GREATER_OR_EQUALS(">="),
    LESS_OR_EQUALS("<="),
    LIKE("LIKE"),
    NOT_LIKE("NOT LIKE"),
    IN("IN"),
    NOT_IN("NOT IN"),
    BETWEEN("BETWEEN"),
    IS_NULL("IS NULL"),
    IS_NOT_NULL("IS NOT NULL")
}