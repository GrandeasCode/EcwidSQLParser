import service.SqlSplitter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SqlSplitterTest {

    private lateinit var splitter: SqlSplitter

    @BeforeEach
    fun setUp() {
        splitter = SqlSplitter()
    }

    @Nested
    @DisplayName("Basic SELECT queries")
    inner class BasicSelectTests {

        @Test
        fun `simple select all`() {
            val sql = "SELECT * FROM users"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.select).isEqualTo("*")
            assertThat(result.from).isEqualTo("users")
            assertThat(result.join).isNull()
            assertThat(result.where).isNull()
            assertThat(result.groups).isNull()
            assertThat(result.having).isNull()
            assertThat(result.sort).isNull()
            assertThat(result.limit).isNull()
            assertThat(result.offset).isNull()
        }

        @Test
        fun `select with specific columns`() {
            val sql = "SELECT id, name, email FROM users"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.select).isEqualTo("id, name, email")
            assertThat(result.from).isEqualTo("users")
        }

        @Test
        fun `select with column aliases`() {
            val sql = "SELECT id, name AS user_name, email AS user_email FROM users"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.select).isEqualTo("id, name AS user_name, email AS user_email")
        }

        @Test
        fun `select with table prefix`() {
            val sql = "SELECT users.id, users.name FROM users"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.select).isEqualTo("users.id, users.name")
        }

        @Test
        fun `case insensitive keywords`() {
            val sql = "select * from users where id = 1"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.select).isEqualTo("*")
            assertThat(result.from).isEqualTo("users")
            assertThat(result.where).isEqualTo("id = 1")
        }

        @Test
        fun `mixed case keywords`() {
            val sql = "SeLeCt * FrOm users WhErE id = 1"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.select).isEqualTo("*")
            assertThat(result.from).isEqualTo("users")
            assertThat(result.where).isEqualTo("id = 1")
        }
    }

    @Nested
    @DisplayName("FROM clause")
    inner class FromClauseTests {

        @Test
        fun `implicit join with multiple tables`() {
            val sql = "SELECT * FROM a, b, c"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.from).isEqualTo("a, b, c")
        }

        @Test
        fun `table with schema`() {
            val sql = "SELECT * FROM public.users"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.from).isEqualTo("public.users")
        }

        @Test
        fun `table with alias`() {
            val sql = "SELECT u.id FROM users u"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.from).isEqualTo("users u")
        }

        @Test
        fun `table with AS alias`() {
            val sql = "SELECT u.id FROM users AS u"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.from).isEqualTo("users AS u")
        }
    }

    @Nested
    @DisplayName("JOIN clauses")
    inner class JoinTests {

        @Test
        fun `simple inner join`() {
            val sql = "SELECT * FROM users JOIN orders ON users.id = orders.user_id"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.from).isEqualTo("users")
            assertThat(result.join).isEqualTo("JOIN orders ON users.id = orders.user_id")
        }

        @Test
        fun `explicit inner join`() {
            val sql = "SELECT * FROM users INNER JOIN orders ON users.id = orders.user_id"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.join).isEqualTo("INNER JOIN orders ON users.id = orders.user_id")
        }

        @ParameterizedTest
        @CsvSource(
            "LEFT JOIN, LEFT JOIN",
            "LEFT OUTER JOIN, LEFT OUTER JOIN",
            "RIGHT JOIN, RIGHT JOIN",
            "RIGHT OUTER JOIN, RIGHT OUTER JOIN",
            "FULL JOIN, FULL JOIN",
            "FULL OUTER JOIN, FULL OUTER JOIN",
            "CROSS JOIN, CROSS JOIN"
        )
        fun `different join types`(joinSyntax: String, expectedPrefix: String) {
            val sql = "SELECT * FROM users $joinSyntax orders ON users.id = orders.user_id"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.join).startsWith(expectedPrefix)
        }

        @Test
        fun `multiple joins`() {
            val sql = """
                SELECT * FROM users 
                LEFT JOIN orders ON users.id = orders.user_id
                INNER JOIN payments ON orders.id = payments.order_id
            """.trimIndent()

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.join)
                .contains("LEFT JOIN orders ON users.id = orders.user_id")
                .contains("INNER JOIN payments ON orders.id = payments.order_id")
        }
    }

    @Nested
    @DisplayName("WHERE clause")
    inner class WhereTests {

        @Test
        fun `simple where condition`() {
            val sql = "SELECT * FROM users WHERE id = 1"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.where).isEqualTo("id = 1")
        }

        @Test
        fun `where with multiple conditions`() {
            val sql = "SELECT * FROM users WHERE id > 10 AND status = 'active' OR role = 'admin'"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.where).isEqualTo("id > 10 AND status = 'active' OR role = 'admin'")
        }

        @Test
        fun `where with IN clause`() {
            val sql = "SELECT * FROM users WHERE status IN ('active', 'pending')"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.where).isEqualTo("status IN ('active', 'pending')")
        }

        @Test
        fun `where with BETWEEN`() {
            val sql = "SELECT * FROM users WHERE age BETWEEN 18 AND 65"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.where).isEqualTo("age BETWEEN 18 AND 65")
        }

        @Test
        fun `where with LIKE`() {
            val sql = "SELECT * FROM users WHERE name LIKE '%john%'"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.where).isEqualTo("name LIKE '%john%'")
        }
    }

    @Nested
    @DisplayName("GROUP BY and HAVING")
    inner class GroupByTests {

        @Test
        fun `simple group by`() {
            val sql = "SELECT status, COUNT(*) FROM users GROUP BY status"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.groups).isEqualTo("status")
        }

        @Test
        fun `group by multiple columns`() {
            val sql = "SELECT status, role, COUNT(*) FROM users GROUP BY status, role"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.groups).isEqualTo("status, role")
        }

        @Test
        fun `group by with having`() {
            val sql = "SELECT status, COUNT(*) FROM users GROUP BY status HAVING COUNT(*) > 5"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.groups).isEqualTo("status")
            assertThat(result.having).isEqualTo("COUNT(*) > 5")
        }

        @Test
        fun `having with multiple conditions`() {
            val sql = """
                SELECT status, COUNT(*), SUM(amount) 
                FROM users 
                GROUP BY status 
                HAVING COUNT(*) > 5 AND SUM(amount) > 1000
            """.trimIndent()

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.having).isEqualTo("COUNT(*) > 5 AND SUM(amount) > 1000")
        }
    }

    @Nested
    @DisplayName("ORDER BY clause")
    inner class OrderByTests {

        @Test
        fun `simple order by`() {
            val sql = "SELECT * FROM users ORDER BY name"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.sort).isEqualTo("name")
        }

        @Test
        fun `order by with direction`() {
            val sql = "SELECT * FROM users ORDER BY name DESC"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.sort).isEqualTo("name DESC")
        }

        @Test
        fun `order by multiple columns`() {
            val sql = "SELECT * FROM users ORDER BY status ASC, name DESC, id"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.sort).isEqualTo("status ASC, name DESC, id")
        }
    }

    @Nested
    @DisplayName("LIMIT and OFFSET")
    inner class LimitOffsetTests {

        @Test
        fun `limit only`() {
            val sql = "SELECT * FROM users LIMIT 10"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.limit).isEqualTo(10)
            assertThat(result.offset).isNull()
        }

        @Test
        fun `limit and offset`() {
            val sql = "SELECT * FROM users LIMIT 10 OFFSET 20"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.limit).isEqualTo(10)
            assertThat(result.offset).isEqualTo(20)
        }

        @Test
        fun `offset without limit`() {
            val sql = "SELECT * FROM users OFFSET 5"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.limit).isNull()
            assertThat(result.offset).isEqualTo(5)
        }
    }

    @Nested
    @DisplayName("Subqueries")
    inner class SubqueryTests {

        @Test
        fun `subquery in FROM`() {
            val sql = "SELECT * FROM (SELECT * FROM users WHERE active = true) u"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.from).isEqualTo("(SELECT * FROM users WHERE active = true) u")
        }

        @Test
        fun `subquery in SELECT`() {
            val sql = "SELECT id, (SELECT COUNT(*) FROM orders WHERE user_id = u.id) as cnt FROM users u"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.select).isEqualTo("id, (SELECT COUNT(*) FROM orders WHERE user_id = u.id) as cnt")
            assertThat(result.from).isEqualTo("users u")
        }

        @Test
        fun `subquery in WHERE`() {
            val sql = "SELECT * FROM users WHERE id IN (SELECT user_id FROM premium_users)"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.where).isEqualTo("id IN (SELECT user_id FROM premium_users)")
        }

        @Test
        fun `subquery in JOIN`() {
            val sql = """
                SELECT * FROM users u 
                LEFT JOIN (SELECT user_id, SUM(amount) as total FROM orders GROUP BY user_id) o 
                ON u.id = o.user_id
            """.trimIndent()

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.join).contains("(SELECT user_id, SUM(amount) as total FROM orders GROUP BY user_id)")
        }

        @Test
        fun `nested subqueries`() {
            val sql = """
                SELECT * FROM users 
                WHERE id IN (SELECT user_id FROM orders WHERE product_id IN (SELECT id FROM products WHERE active = true))
            """.trimIndent()

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.where).isEqualTo(
                "id IN (SELECT user_id FROM orders WHERE product_id IN (SELECT id FROM products WHERE active = true))"
            )
        }

        @Test
        fun `multiple subqueries in different clauses`() {
            val sql = """
                SELECT 
                    u.name,
                    (SELECT COUNT(*) FROM orders WHERE user_id = u.id) as order_count
                FROM (SELECT * FROM users WHERE active = true) u
                WHERE u.id IN (SELECT user_id FROM premium)
                ORDER BY order_count DESC
            """.trimIndent()

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.select).contains("(SELECT COUNT(*) FROM orders WHERE user_id = u.id)")
            assertThat(result.from).contains("(SELECT * FROM users WHERE active = true)")
            assertThat(result.where).contains("(SELECT user_id FROM premium)")
        }
    }

    @Nested
    @DisplayName("String literals with keywords")
    inner class StringLiteralTests {

        @Test
        fun `single quotes with SELECT keyword inside`() {
            val sql = "SELECT * FROM users WHERE query = 'SELECT * FROM hackers'"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.select).isEqualTo("*")
            assertThat(result.from).isEqualTo("users")
            assertThat(result.where).isEqualTo("query = 'SELECT * FROM hackers'")
        }

        @Test
        fun `single quotes with FROM keyword inside`() {
            val sql = "SELECT * FROM users WHERE status = 'FROM somewhere'"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.where).isEqualTo("status = 'FROM somewhere'")
        }

        @Test
        fun `double quotes with keywords inside`() {
            val sql = """SELECT * FROM users WHERE name = "SELECT FROM WHERE""""

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.where).isEqualTo("""name = "SELECT FROM WHERE"""")
        }

        @Test
        fun `escaped single quotes`() {
            val sql = "SELECT * FROM users WHERE name = 'It''s a test'"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.where).isEqualTo("name = 'It''s a test'")
        }

        @Test
        fun `escaped double quotes`() {
            val sql = "SELECT * FROM users WHERE name = \"He said \"\"hi\"\"\""

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.select).isEqualTo("*")
            assertThat(result.from).isEqualTo("users")
            assertThat(result.where).isEqualTo("name = \"He said \"\"hi\"\"\"")
        }

        @Test
        fun `parentheses inside string`() {
            val sql = "SELECT * FROM users WHERE name = 'John (test)'"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.where).isEqualTo("name = 'John (test)'")
        }
    }

    @Nested
    @DisplayName("Comments")
    inner class CommentTests {

        @Test
        fun `line comment with keyword`() {
            val sql = """
                SELECT * FROM users -- WHERE fake = true
                WHERE id = 1
            """.trimIndent()

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.where).isEqualTo("id = 1")
        }

        @Test
        fun `block comment with keyword`() {
            val sql = "SELECT * FROM users /* WHERE fake FROM table */ WHERE id = 1"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.where).isEqualTo("id = 1")
        }

        @Test
        fun `multiple block comments`() {
            val sql = "SELECT /* comment1 */ * FROM /* comment2 */ users WHERE /* comment3 */ id = 1"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.select).contains("*")
            assertThat(result.from).contains("users")
            assertThat(result.where).contains("id = 1")
        }
    }

    @Nested
    @DisplayName("Aggregate functions")
    inner class AggregateFunctionTests {

        @Test
        fun `count function`() {
            val sql = "SELECT COUNT(*) FROM users"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.select).isEqualTo("COUNT(*)")
        }

        @Test
        fun `multiple aggregate functions`() {
            val sql = "SELECT COUNT(*), SUM(amount), AVG(price), MIN(date), MAX(date) FROM orders"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.select).isEqualTo("COUNT(*), SUM(amount), AVG(price), MIN(date), MAX(date)")
        }

        @Test
        fun `count distinct`() {
            val sql = "SELECT COUNT(DISTINCT status) FROM users"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.select).isEqualTo("COUNT(DISTINCT status)")
        }
    }

    @Nested
    @DisplayName("Complex queries")
    inner class ComplexQueryTests {

        @Test
        fun `full featured query`() {
            val sql = """
                SELECT 
                    author.name, 
                    COUNT(book.id) as book_count, 
                    SUM(book.cost) as total_cost
                FROM author
                LEFT JOIN book ON author.id = book.author_id
                WHERE author.active = true
                GROUP BY author.name
                HAVING COUNT(*) > 1 AND SUM(book.cost) > 500
                ORDER BY total_cost DESC
                LIMIT 10
                OFFSET 5
            """.trimIndent()

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.select).contains("author.name", "COUNT(book.id)", "SUM(book.cost)")
            assertThat(result.from).isEqualTo("author")
            assertThat(result.join).contains("LEFT JOIN book ON author.id = book.author_id")
            assertThat(result.where).isEqualTo("author.active = true")
            assertThat(result.groups).isEqualTo("author.name")
            assertThat(result.having).isEqualTo("COUNT(*) > 1 AND SUM(book.cost) > 500")
            assertThat(result.sort).isEqualTo("total_cost DESC")
            assertThat(result.limit).isEqualTo(10)
            assertThat(result.offset).isEqualTo(5)
        }

        @Test
        fun `query with everything combined`() {
            val sql = """
                SELECT 
                    u.name,
                    (SELECT COUNT(*) FROM orders o WHERE o.user_id = u.id) as order_count
                FROM (SELECT * FROM users WHERE role IN (SELECT role FROM allowed_roles)) u
                LEFT JOIN payments p ON u.id = p.user_id
                WHERE u.status IN ('active', 'pending (review)')
                    AND u.created_at > (SELECT MIN(created_at) FROM users)
                GROUP BY u.name
                HAVING order_count > 0
                ORDER BY order_count DESC
                LIMIT 10
            """.trimIndent()

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.select).contains("(SELECT COUNT(*) FROM orders o WHERE o.user_id = u.id)")
            assertThat(result.from).contains("(SELECT * FROM users WHERE role IN (SELECT role FROM allowed_roles))")
            assertThat(result.where).contains("(SELECT MIN(created_at) FROM users)")
        }
    }

    @Nested
    @DisplayName("Edge cases")
    inner class EdgeCaseTests {

        @Test
        fun `query with trailing semicolon`() {
            val sql = "SELECT * FROM users;"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.from).isEqualTo("users")
        }

        @Test
        fun `query with extra whitespace`() {
            val sql = "SELECT    *    FROM    users    WHERE    id = 1"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.select).isEqualTo("*")
            assertThat(result.from).isEqualTo("users")
            assertThat(result.where).isEqualTo("id = 1")
        }

        @Test
        fun `query with newlines and tabs`() {
            val sql = "SELECT\n\t*\nFROM\n\tusers"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.select).isEqualTo("*")
            assertThat(result.from).isEqualTo("users")
        }

        @Test
        fun `column name similar to keyword`() {
            val sql = "SELECT selected, fromage, ordering FROM users"

            val result = splitter.formSplitSqlQuery(sql)

            assertThat(result.select).isEqualTo("selected, fromage, ordering")
            assertThat(result.from).isEqualTo("users")
        }
    }

    @Nested
    @DisplayName("Error handling")
    inner class ErrorHandlingTests {

        @Test
        fun `unbalanced parentheses - missing close`() {
            val sql = "SELECT * FROM (SELECT * FROM users"

            assertThatThrownBy { splitter.formSplitSqlQuery(sql) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Unbalanced parentheses")
        }

        @Test
        fun `unbalanced parentheses - extra close`() {
            val sql = "SELECT * FROM users)"

            assertThatThrownBy { splitter.formSplitSqlQuery(sql) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Unbalanced parentheses")
        }

        @Test
        fun `unclosed single quote`() {
            val sql = "SELECT * FROM users WHERE name = 'test"

            assertThatThrownBy { splitter.formSplitSqlQuery(sql) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Unclosed single quote")
        }

        @Test
        fun `unclosed double quote`() {
            val sql = """SELECT * FROM users WHERE name = "test"""

            assertThatThrownBy { splitter.formSplitSqlQuery(sql) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Unclosed double quote")
        }
    }
}