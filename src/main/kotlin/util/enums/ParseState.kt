package util.enums

enum class ParseState {
    NORMAL,
    SINGLE_QUOTE,
    DOUBLE_QUOTE,
    LINE_COMMENT,
    BLOCK_COMMENT
}