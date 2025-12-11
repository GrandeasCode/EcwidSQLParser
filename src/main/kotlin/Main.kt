import service.SqlParser
import service.SqlSplitter

val parser = SqlParser()

fun main(args: Array<String>) {
    val sqlQuery = if (args.contains("--demo")) {
        getDemoQuery()
    } else {
        println("=== SQL Parser ===")
        println("Enter SQL query.")
        println("To exit the command:")
        println("  - enter ';' in the end of the query, or")
        println("  - enter empty line 2 times in the row")
        println()
        readMultilineSql()
    }

    if (sqlQuery.isBlank()) {
        println("Empty request.")
        return
    }

    try {
        val splitSqlQuery = SqlSplitter().formSplitSqlQuery(sqlQuery)
        val parsedQuery = parser.parseQuery(splitSqlQuery)

        val errors = parser.validateParsedQuery(parsedQuery)
        if (errors.isNotEmpty()) {
            println("Validation errors:")
            errors.forEach { println("  - $it") }
            return
        }

        println()
        println("=== Parsed SQL ===")
        println(parsedQuery.toSql())

    } catch (exception: Exception) {
        println("Error: ${exception.message}")
        exception.printStackTrace()
    }
}

fun readMultilineSql(): String {
    val lines = mutableListOf<String>()
    var consecutiveEmptyLines = 0

    while (true) {
        val line = readlnOrNull() ?: break

        if (line.isBlank()) {
            consecutiveEmptyLines++

            if (consecutiveEmptyLines >= 2 && lines.any { it.isNotBlank() }) {
                break
            }

            lines.add(line)
            continue
        }

        consecutiveEmptyLines = 0
        lines.add(line)

        if (line.trimEnd().endsWith(";")) {
            break
        }
    }

    return lines.joinToString("\n")
        .trimEnd(';')
        .trim()
}

fun getDemoQuery(): String = """
SELECT
    c.customer_id,
    c.name,

    -- 3 вложенных друг в друга subquery в SELECT
    (
        SELECT
            (
                SELECT
                    (
                        SELECT MAX(p3.price)
                        FROM products p3
                        WHERE p3.category_id = p2.category_id
                    )
                FROM products p2
                WHERE p2.product_id = p1.product_id
            )
        FROM products p1
        WHERE p1.product_id = c.favorite_product_id
    ) AS triple_nested_max_price,

    -- подзапрос в SELECT (счёт заказов)
    (
        SELECT COUNT(*)
        FROM orders o2
        WHERE o2.customer_id = c.customer_id
          AND o2.status = 'CANCELLED'
    ) AS cancelled_orders,

    ro.total_spent_last_year

FROM customers c

-- подзапрос в FROM (derived table)
JOIN (
    SELECT
        o.customer_id,
        SUM(o.total_amount) AS total_spent_last_year
    FROM orders o
    WHERE o.order_date >= DATE '2024-01-01'
      AND o.order_date <  DATE '2025-01-01'

      -- подзапрос в WHERE через EXISTS
      AND EXISTS (
          SELECT 1
          FROM payments pay
          WHERE pay.order_id = o.order_id
            AND pay.status = 'CONFIRMED'
      )
    GROUP BY o.customer_id
) ro ON ro.customer_id = c.customer_id

WHERE
    c.status = 'ACTIVE'

    -- подзапрос в WHERE через IN
    AND c.customer_id IN (
        SELECT DISTINCT o3.customer_id
        FROM orders o3
        WHERE o3.order_date >= CURRENT_DATE - INTERVAL '90 days'
    )

GROUP BY
    c.customer_id,
    c.name,
    ro.total_spent_last_year

-- подзапрос в HAVING
HAVING ro.total_spent_last_year >
    (
        SELECT AVG(customer_total)
        FROM (
            SELECT
                o4.customer_id,
                SUM(o4.total_amount) AS customer_total
            FROM orders o4
            GROUP BY o4.customer_id
        ) totals
    )

-- подзапрос в ORDER BY
ORDER BY
    (
        SELECT COALESCE(MAX(o5.order_date), DATE '1900-01-01')
        FROM orders o5
        WHERE o5.customer_id = c.customer_id
    ) DESC;

""".trimIndent()