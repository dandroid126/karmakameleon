package com.karmakameleon.shared.domain.markdown

// How a table column's cells are aligned: set by a colon at the start (left), end (right) or both ends (center) of the
// column's dashes in the separator row, or by none (default).
enum class TableAlignment {
    Default,
    Left,
    Center,
    Right,
}
