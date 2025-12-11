package model.data

import util.SqlChars.CLOSE_PARENTHESES
import util.SqlChars.DOUBLE_QUOTE
import util.SqlChars.OPEN_PARENTHESES
import util.SqlChars.SINGLE_QUOTE

data class ParserState(
    var depth: Int = 0,
    var inSingleQuote: Boolean = false,
    var inDoubleQuote: Boolean = false
) {
    fun isTopLevel(): Boolean = depth == 0 && !inSingleQuote && !inDoubleQuote

    fun processCharacter(character: Char) {
        when (character) {
            SINGLE_QUOTE -> if (!inDoubleQuote) inSingleQuote = !inSingleQuote
            DOUBLE_QUOTE -> if (!inSingleQuote) inDoubleQuote = !inDoubleQuote
            OPEN_PARENTHESES -> if (!inSingleQuote && !inDoubleQuote) depth++
            CLOSE_PARENTHESES -> if (!inSingleQuote && !inDoubleQuote) depth--
        }
    }
}