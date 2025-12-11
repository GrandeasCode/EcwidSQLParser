package service

import model.data.KeywordPosition
import model.data.MatchedKeyword
import model.query.SplitSqlQuery
import util.SqlChars.ASTERISK
import util.SqlChars.CLOSE_PARENTHESES
import util.SqlChars.DASH
import util.SqlChars.DOUBLE_QUOTE
import util.SqlChars.NEWLINE
import util.SqlChars.OPEN_PARENTHESES
import util.SqlChars.SEMICOLON
import util.SqlChars.SINGLE_QUOTE
import util.SqlChars.SLASH
import util.SqlChars.SPACE
import util.SqlChars.UNDERSCORE
import util.SqlKeywords.FROM
import util.SqlKeywords.GROUP_BY
import util.SqlKeywords.HAVING
import util.SqlKeywords.JOIN_KEYWORDS_SET
import util.SqlKeywords.LIMIT
import util.SqlKeywords.MAIN_KEYWORDS
import util.SqlKeywords.OFFSET
import util.SqlKeywords.ORDER_BY_TEXT
import util.SqlKeywords.SELECT
import util.SqlKeywords.WHERE
import util.enums.ParseState
import util.enums.ParseState.BLOCK_COMMENT
import util.enums.ParseState.LINE_COMMENT
import util.enums.ParseState.NORMAL
import util.enums.ParseState.DOUBLE_QUOTE as STATE_DOUBLE_QUOTE
import util.enums.ParseState.SINGLE_QUOTE as STATE_SINGLE_QUOTE

class SqlSplitter {

    fun formSplitSqlQuery(sqlText: String): SplitSqlQuery {
        val (cleanedSqlText, keywordPositions) = parseAndNormalize(sqlText.trim())
        val sections = extractAllSections(cleanedSqlText, keywordPositions)

        return SplitSqlQuery(
            select = sections[SELECT]
                ?: throw IllegalArgumentException("No SELECT query section."),
            from = sections[FROM]
                ?: throw IllegalArgumentException("No FROM query section."),
            join = sections["JOIN"],
            where = sections[WHERE],
            groups = sections[GROUP_BY],
            having = sections[HAVING],
            sort = sections[ORDER_BY_TEXT],
            limit = sections[LIMIT]?.toIntOrNull(),
            offset = sections[OFFSET]?.toIntOrNull()
        )
    }

    private fun parseAndNormalize(sqlText: String): Pair<String, List<KeywordPosition>> {
        val result = StringBuilder()
        val keywords = mutableListOf<KeywordPosition>()

        var state = NORMAL
        var depth = 0
        var index = 0
        var lastWasWhitespace = false

        while (index < sqlText.length) {
            val char = sqlText[index]
            val nextCharacter = sqlText.getOrNull(index + 1)

            when (state) {
                NORMAL -> {
                    when {
                        char.isWhitespace() -> {
                            if (!lastWasWhitespace) {
                                result.append(SPACE)
                                lastWasWhitespace = true
                            }
                            index++
                            continue
                        }
                        char == SINGLE_QUOTE -> {
                            lastWasWhitespace = false
                            result.append(char)
                            state = STATE_SINGLE_QUOTE
                        }
                        char == DOUBLE_QUOTE -> {
                            lastWasWhitespace = false
                            result.append(char)
                            state = STATE_DOUBLE_QUOTE
                        }
                        char == DASH && nextCharacter == DASH -> {
                            state = LINE_COMMENT
                            index += 2
                            continue
                        }
                        char == SLASH && nextCharacter == ASTERISK -> {
                            state = BLOCK_COMMENT
                            index += 2
                            continue
                        }
                        char == OPEN_PARENTHESES -> {
                            lastWasWhitespace = false
                            depth++
                            result.append(char)
                        }
                        char == CLOSE_PARENTHESES -> {
                            lastWasWhitespace = false
                            depth--
                            if (depth < 0) {
                                throw IllegalArgumentException("Unbalanced parentheses at position $index")
                            }
                            result.append(char)
                        }
                        depth == 0 -> {
                            val keyword = tryMatchKeyword(sqlText, index)
                            if (keyword != null) {
                                lastWasWhitespace = false
                                keywords.add(KeywordPosition(keyword.name, result.length, keyword.name.length))
                                result.append(keyword.name)
                                index += keyword.length
                                continue
                            } else {
                                lastWasWhitespace = false
                                result.append(char)
                            }
                        }
                        else -> {
                            lastWasWhitespace = false
                            result.append(char)
                        }
                    }
                }

                STATE_SINGLE_QUOTE -> {
                    result.append(char)
                    when (char) {
                        SINGLE_QUOTE if nextCharacter == SINGLE_QUOTE -> {
                            index++
                            result.append(SINGLE_QUOTE)
                        }
                        SINGLE_QUOTE -> {
                            lastWasWhitespace = false
                            state = NORMAL
                        }
                    }
                }

                STATE_DOUBLE_QUOTE -> {
                    result.append(char)
                    when (char) {
                        DOUBLE_QUOTE if nextCharacter == DOUBLE_QUOTE -> {
                            index++
                            result.append(DOUBLE_QUOTE)
                        }
                        DOUBLE_QUOTE -> {
                            lastWasWhitespace = false
                            state = NORMAL
                        }
                    }
                }

                LINE_COMMENT -> {
                    if (char == NEWLINE) {
                        if (!lastWasWhitespace) {
                            result.append(SPACE)
                            lastWasWhitespace = true
                        }
                        state = NORMAL
                    }
                }

                BLOCK_COMMENT -> {
                    if (char == ASTERISK && nextCharacter == SLASH) {
                        if (!lastWasWhitespace) {
                            result.append(SPACE)
                            lastWasWhitespace = true
                        }
                        state = NORMAL
                        index += 2
                        continue
                    }
                }
            }
            index++
        }

        validateFinalState(depth, state)
        return result.toString().trim() to keywords
    }

    private fun validateFinalState(depth: Int, state: ParseState) {
        when {
            depth != 0 -> throw IllegalArgumentException("Unbalanced parentheses: $depth unclosed")
            state == STATE_SINGLE_QUOTE -> throw IllegalArgumentException("Unclosed single quote")
            state == STATE_DOUBLE_QUOTE -> throw IllegalArgumentException("Unclosed double quote")
            state == BLOCK_COMMENT -> throw IllegalArgumentException("Unclosed block comment")
        }
    }

    private fun extractAllSections(sqlText: String, positions: List<KeywordPosition>): Map<String, String> {
        if (positions.isEmpty()) return emptyMap()

        val sections = mutableMapOf<String, String>()
        val joinParts = mutableListOf<String>()

        positions.forEachIndexed { index, current ->
            val contentStart = current.position + current.length
            val contentEnd = positions.getOrNull(index + 1)?.position ?: sqlText.length
            val content = sqlText.substring(contentStart, contentEnd)
                .trim()
                .trimEnd(SEMICOLON)

            if (content.isEmpty()) return@forEachIndexed

            if (current.keyword in JOIN_KEYWORDS_SET) {
                joinParts.add("${current.keyword} $content")
            } else {
                sections[current.keyword] = content
            }
        }

        if (joinParts.isNotEmpty()) {
            sections["JOIN"] = joinParts.joinToString(" ")
        }

        return sections
    }

    private fun tryMatchKeyword(sqlText: String, position: Int): MatchedKeyword? {
        if (position > 0 && sqlText[position - 1].isLetterOrDigit()) return null

        val remaining = sqlText.substring(position)

        for (keyword in MAIN_KEYWORDS) {
            matchesKeywordLength(remaining, keyword)?.let { length ->
                return MatchedKeyword(keyword, length)
            }
        }

        return null
    }

    private fun matchesKeywordLength(value: String, keyword: String): Int? {
        var stringIndex = 0
        var keywordIndex = 0

        while (keywordIndex < keyword.length && stringIndex < value.length) {
            val keywordCharacter = keyword[keywordIndex]
            val stringCharacter = value[stringIndex]

            if (keywordCharacter == SPACE) {
                if (!stringCharacter.isWhitespace()) return null
                while (stringIndex < value.length && value[stringIndex].isWhitespace()) stringIndex++
                keywordIndex++
            } else {
                if (!stringCharacter.equals(keywordCharacter, ignoreCase = true)) return null
                stringIndex++
                keywordIndex++
            }
        }

        if (keywordIndex < keyword.length) return null
        if (stringIndex < value.length) {
            val nextCharacter = value[stringIndex]
            if (nextCharacter.isLetterOrDigit() || nextCharacter == UNDERSCORE) return null
        }

        return stringIndex
    }
}