# SQL Query Parser

A Kotlin-based SQL SELECT query parser that tokenizes, parses, and reconstructs SQL queries. The parser supports complex nested subqueries, various JOIN types, WHERE conditions, GROUP BY, HAVING, ORDER BY, and more.

## Features

- **Full SELECT query parsing** — columns, aliases, aggregate functions, subqueries
- **FROM clause** — tables, schemas, aliases, derived tables (subqueries)
- **JOIN support** — INNER, LEFT, RIGHT, FULL, CROSS joins with complex ON conditions
- **WHERE conditions** — comparison operators, IN, NOT IN, LIKE, BETWEEN, IS NULL, subqueries
- **GROUP BY and HAVING** — with aggregate function support
- **ORDER BY** — ASC/DESC, NULLS FIRST/LAST, subqueries
- **LIMIT and OFFSET**
- **Nested subqueries** — unlimited nesting depth in SELECT, FROM, WHERE, HAVING, ORDER BY
- **Comment handling** — removes `--` line comments and `/* */` block comments
- **String literal preservation** — correctly handles escaped quotes (`''`, `""`)

## Requirements

- Java 21+
- Kotlin 2.0+
- Gradle 8.0+
