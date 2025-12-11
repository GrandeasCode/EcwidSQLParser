package model.query.join

sealed class JoinOperand {

    data class Column(
        val name: String,
        val table: String? = null
    ) : JoinOperand()

    data class Value(val sqlValue: String) : JoinOperand()
}

fun JoinOperand.toSql(): String = when (this) {
    is JoinOperand.Column -> table?.let { "$it.$name" } ?: name
    is JoinOperand.Value -> sqlValue
}