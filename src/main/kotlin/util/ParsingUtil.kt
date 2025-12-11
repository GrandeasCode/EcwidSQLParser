package util

import model.data.ExpressionWithOperator
import model.data.ParsedAggregateFunction
import util.SqlChars.CLOSE_PARENTHESES
import util.SqlChars.COMMA
import util.SqlChars.OPEN_PARENTHESES
import model.data.ParserState
import util.SqlChars.SPACE
import util.SqlKeywords.AND
import util.SqlKeywords.AS
import util.SqlKeywords.BETWEEN
import util.SqlKeywords.DISTINCT
import util.SqlKeywords.OR
import util.SqlKeywords.SELECT
import util.enums.AggregateFunctionType
import util.enums.ComparisonOperator
import util.enums.ComparisonOperator.EQUALS
import util.enums.ComparisonOperator.GREATER_OR_EQUALS
import util.enums.ComparisonOperator.GREATER_THAN
import util.enums.ComparisonOperator.LESS_OR_EQUALS
import util.enums.ComparisonOperator.LESS_THAN
import util.enums.ComparisonOperator.NOT_EQUALS
import util.enums.LogicalOperator
import kotlin.text.iterator

open class ParsingUtil {

    open fun splitByComma(string: String): List<String> = splitBy(string, COMMA)

    open fun splitBy(string: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val state = ParserState()
        val current = StringBuilder()

        for (character in string) {
            state.processCharacter(character)

            if (character == delimiter && state.isTopLevel()) {
                result.add(current.toString())
                current.clear()
            } else {
                current.append(character)
            }
        }

        if (current.isNotEmpty()) {
            result.add(current.toString())
        }

        return result
    }

    open fun extractAlias(expression: String): Pair<String, String?> {
        val trimmed = expression.trim()

        val asIndex = findKeywordIndex(trimmed, AS)
        if (asIndex != -1) {
            return trimmed.take(asIndex).trim() to
                    trimmed.substring(asIndex + AS.length).trim()
        }

        val lastSpaceIndex = findLastSpaceOutsideParentheses(trimmed)
        if (lastSpaceIndex != -1) {
            val potentialAlias = trimmed.substring(lastSpaceIndex + 1)
            if (isValidIdentifier(potentialAlias)) {
                return trimmed.take(lastSpaceIndex).trim() to potentialAlias
            }
        }

        return trimmed to null
    }

    open fun findKeywordIndex(string: String, keyword: String): Int {
        val state = ParserState()
        val keywordUppercase = keyword.uppercase()

        var index = 0
        while (index < string.length) {
            state.processCharacter(string[index])

            if (state.isTopLevel() && string.substring(index).uppercase().startsWith(keywordUppercase)) {
                if (isWordBoundary(string, index, keyword.length)) {
                    return index
                }
            }
            index++
        }

        return -1
    }

    open fun findLastSpaceOutsideParentheses(string: String): Int {
        val state = ParserState()
        var lastSpaceIndex = -1

        string.forEachIndexed { index, character ->
            state.processCharacter(character)
            if (character == SPACE && state.isTopLevel()) {
                lastSpaceIndex = index
            }
        }

        return lastSpaceIndex
    }

    open fun isSubQuery(string: String): Boolean {
        val trimmed = string.trim()
        if (!trimmed.startsWith(OPEN_PARENTHESES) || !trimmed.endsWith(CLOSE_PARENTHESES)) return false
        return trimmed.drop(1).trim().uppercase().startsWith(SELECT)
    }

    open fun isAggregateFunction(expression: String): Boolean {
        val uppercase = expression.uppercase()
        return AggregateFunctionType.entries.any { uppercase.startsWith("${it.name}$OPEN_PARENTHESES") }
    }

    open fun isValidIdentifier(string: String): Boolean =
        string.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))

    open fun isColumnReference(string: String): Boolean =
        string.matches(Regex("[a-zA-Z_][a-zA-Z0-9_.]*"))


    open fun parseAggregateFunction(expression: String): ParsedAggregateFunction {
        val uppercase = expression.uppercase()
        val functionType = AggregateFunctionType.entries.first {
            uppercase.startsWith("${it.name}$OPEN_PARENTHESES")
        }

        val innerContent = expression
            .substringAfter(OPEN_PARENTHESES)
            .substringBeforeLast(CLOSE_PARENTHESES)
            .trim()

        val distinct = innerContent.uppercase().startsWith(DISTINCT)
        val argument = if (distinct) {
            innerContent.substringAfter(DISTINCT).trim()
        } else {
            innerContent
        }

        return ParsedAggregateFunction(functionType, argument, distinct)
    }

    open fun findComparisonOperator(expression: String): ComparisonOperator {
        val operatorMap = listOf(
            ">=" to GREATER_OR_EQUALS,
            "<=" to LESS_OR_EQUALS,
            "!=" to NOT_EQUALS,
            "<>" to NOT_EQUALS,
            ">" to GREATER_THAN,
            "<" to LESS_THAN,
            "=" to EQUALS
        )

        return operatorMap
            .firstOrNull { (symbol, _) -> expression.contains(symbol) }
            ?.second
            ?: EQUALS
    }

    open fun splitByLogicalOperators(string: String): List<ExpressionWithOperator> {
        val result = mutableListOf<ExpressionWithOperator>()
        val state = ParserState()
        val current = StringBuilder()
        var index = 0

        while (index < string.length) {
            val character = string[index]
            state.processCharacter(character)

            if (state.isTopLevel()) {
                val remaining = string.substring(index).uppercase()

                if (matchesLogicalOperator(remaining, AND)) {
                    val beforeAnd = current.toString().trim().uppercase()
                    if (!isBetweenAnd(beforeAnd)) {
                        result.add(ExpressionWithOperator(current.toString().trim(), LogicalOperator.AND))
                        current.clear()
                        index += AND.length
                        continue
                    }
                }

                if (matchesLogicalOperator(remaining, OR)) {
                    result.add(ExpressionWithOperator(current.toString().trim(), LogicalOperator.OR))
                    current.clear()
                    index += OR.length
                    continue
                }
            }

            current.append(character)
            index++
        }

        if (current.isNotEmpty()) {
            result.add(ExpressionWithOperator(current.toString().trim(), null))
        }

        return result
    }

    private fun isWordBoundary(string: String, start: Int, length: Int): Boolean {
        val before = if (start > 0) string[start - 1] else SPACE
        val after = if (start + length < string.length) string[start + length] else SPACE
        return !before.isLetterOrDigit() && !after.isLetterOrDigit()
    }

    private fun matchesLogicalOperator(remaining: String, operator: String): Boolean =
        remaining.startsWith("$operator ") || remaining.startsWith("$operator$OPEN_PARENTHESES")

    private fun isBetweenAnd(beforeAnd: String): Boolean =
        beforeAnd.contains(BETWEEN) &&
                !beforeAnd.substringAfterLast(BETWEEN).contains(" $AND ")
}