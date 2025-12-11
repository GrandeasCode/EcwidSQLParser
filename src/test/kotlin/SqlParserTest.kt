import model.query.SplitSqlQuery
import model.query.join.JoinOperand
import model.query.select.SelectExpression
import model.query.where.WhereOperand
import model.query.clause.SourceClause
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import service.SqlParser
import util.enums.AggregateFunctionType
import util.enums.ComparisonOperator
import util.enums.JoinType
import util.enums.LogicalOperator
import util.enums.NullsPosition
import util.enums.SortDirection

class SqlParserTest {

    private lateinit var parser: SqlParser

    @BeforeEach
    fun setUp() {
        parser = SqlParser()
    }

    @Nested
    @DisplayName("SELECT parsing")
    inner class SelectParsingTests {

        @Test
        fun `parse simple column`() {
            val split = createSplit(select = "name")

            val result = parser.parseQuery(split)

            assertThat(result.columns).hasSize(1)
            val expr = result.columns[0].expression
            assertThat(expr).isInstanceOf(SelectExpression.Column::class.java)
            assertThat((expr as SelectExpression.Column).name).isEqualTo("name")
            assertThat(expr.table).isNull()
        }

        @Test
        fun `parse column with table prefix`() {
            val split = createSplit(select = "users.name")

            val result = parser.parseQuery(split)

            assertThat(result.columns).hasSize(1)
            val expr = result.columns[0].expression as SelectExpression.Column
            assertThat(expr.name).isEqualTo("name")
            assertThat(expr.table).isEqualTo("users")
        }

        @Test
        fun `parse multiple columns`() {
            val split = createSplit(select = "id, name, email")

            val result = parser.parseQuery(split)

            assertThat(result.columns).hasSize(3)
            assertThat((result.columns[0].expression as SelectExpression.Column).name).isEqualTo("id")
            assertThat((result.columns[1].expression as SelectExpression.Column).name).isEqualTo("name")
            assertThat((result.columns[2].expression as SelectExpression.Column).name).isEqualTo("email")
        }

        @Test
        fun `parse asterisk`() {
            val split = createSplit(select = "*")

            val result = parser.parseQuery(split)

            assertThat(result.columns).hasSize(1)
            val expr = result.columns[0].expression
            assertThat(expr).isInstanceOf(SelectExpression.AllColumns::class.java)
            assertThat((expr as SelectExpression.AllColumns).table).isNull()
        }

        @Test
        fun `parse table asterisk`() {
            val split = createSplit(select = "users.*")

            val result = parser.parseQuery(split)

            assertThat(result.columns).hasSize(1)
            val expr = result.columns[0].expression as SelectExpression.AllColumns
            assertThat(expr.table).isEqualTo("users")
        }

        @Test
        fun `parse column with alias using AS`() {
            val split = createSplit(select = "name AS user_name")

            val result = parser.parseQuery(split)

            assertThat(result.columns).hasSize(1)
            assertThat(result.columns[0].alias).isEqualTo("user_name")
            assertThat((result.columns[0].expression as SelectExpression.Column).name).isEqualTo("name")
        }

        @Test
        fun `parse column with alias without AS`() {
            val split = createSplit(select = "name user_name")

            val result = parser.parseQuery(split)

            assertThat(result.columns).hasSize(1)
            assertThat(result.columns[0].alias).isEqualTo("user_name")
        }

        @Test
        fun `parse COUNT function`() {
            val split = createSplit(select = "COUNT(id)")

            val result = parser.parseQuery(split)

            assertThat(result.columns).hasSize(1)
            val expr = result.columns[0].expression as SelectExpression.Function
            assertThat(expr.functionType).isEqualTo(AggregateFunctionType.COUNT)
            assertThat(expr.distinct).isFalse()
            assertThat((expr.argument as SelectExpression.Column).name).isEqualTo("id")
        }

        @Test
        fun `parse COUNT with DISTINCT`() {
            val split = createSplit(select = "COUNT(DISTINCT status)")

            val result = parser.parseQuery(split)

            val expr = result.columns[0].expression as SelectExpression.Function
            assertThat(expr.functionType).isEqualTo(AggregateFunctionType.COUNT)
            assertThat(expr.distinct).isTrue()
            assertThat((expr.argument as SelectExpression.Column).name).isEqualTo("status")
        }

        @Test
        fun `parse COUNT asterisk`() {
            val split = createSplit(select = "COUNT(*)")

            val result = parser.parseQuery(split)

            val expr = result.columns[0].expression as SelectExpression.Function
            assertThat(expr.functionType).isEqualTo(AggregateFunctionType.COUNT)
            assertThat(expr.argument).isInstanceOf(SelectExpression.AllColumns::class.java)
        }

        @Test
        fun `parse SUM function`() {
            val split = createSplit(select = "SUM(amount)")

            val result = parser.parseQuery(split)

            val expr = result.columns[0].expression as SelectExpression.Function
            assertThat(expr.functionType).isEqualTo(AggregateFunctionType.SUM)
        }

        @Test
        fun `parse AVG function`() {
            val split = createSplit(select = "AVG(price)")

            val result = parser.parseQuery(split)

            val expr = result.columns[0].expression as SelectExpression.Function
            assertThat(expr.functionType).isEqualTo(AggregateFunctionType.AVG)
        }

        @Test
        fun `parse MIN function`() {
            val split = createSplit(select = "MIN(created_at)")

            val result = parser.parseQuery(split)

            val expr = result.columns[0].expression as SelectExpression.Function
            assertThat(expr.functionType).isEqualTo(AggregateFunctionType.MIN)
        }

        @Test
        fun `parse MAX function`() {
            val split = createSplit(select = "MAX(updated_at)")

            val result = parser.parseQuery(split)

            val expr = result.columns[0].expression as SelectExpression.Function
            assertThat(expr.functionType).isEqualTo(AggregateFunctionType.MAX)
        }

        @Test
        fun `parse function with alias`() {
            val split = createSplit(select = "COUNT(id) AS total")

            val result = parser.parseQuery(split)

            assertThat(result.columns[0].alias).isEqualTo("total")
            assertThat(result.columns[0].expression).isInstanceOf(SelectExpression.Function::class.java)
        }

        @Test
        fun `parse function with table prefix`() {
            val split = createSplit(select = "COUNT(orders.id)")

            val result = parser.parseQuery(split)

            val expr = result.columns[0].expression as SelectExpression.Function
            val arg = expr.argument as SelectExpression.Column
            assertThat(arg.table).isEqualTo("orders")
            assertThat(arg.name).isEqualTo("id")
        }

        @Test
        fun `parse subquery in select`() {
            val split = createSplit(select = "(SELECT COUNT(*) FROM orders WHERE user_id = u.id) AS order_count")

            val result = parser.parseQuery(split)

            assertThat(result.columns).hasSize(1)
            assertThat(result.columns[0].alias).isEqualTo("order_count")
            val expr = result.columns[0].expression
            assertThat(expr).isInstanceOf(SelectExpression.SubQuery::class.java)
        }

        @Test
        fun `parse mixed columns and functions`() {
            val split = createSplit(select = "u.name, COUNT(o.id) AS orders, SUM(o.amount) AS total")

            val result = parser.parseQuery(split)

            assertThat(result.columns).hasSize(3)
            assertThat(result.columns[0].expression).isInstanceOf(SelectExpression.Column::class.java)
            assertThat(result.columns[1].expression).isInstanceOf(SelectExpression.Function::class.java)
            assertThat(result.columns[2].expression).isInstanceOf(SelectExpression.Function::class.java)
        }
    }

    @Nested
    @DisplayName("FROM parsing")
    inner class FromParsingTests {

        @Test
        fun `parse simple table`() {
            val split = createSplit(from = "users")

            val result = parser.parseQuery(split)

            assertThat(result.from).hasSize(1)
            val source = result.from[0] as SourceClause.Table
            assertThat(source.name).isEqualTo("users")
            assertThat(source.alias).isNull()
            assertThat(source.schema).isNull()
        }

        @Test
        fun `parse table with alias using AS`() {
            val split = createSplit(from = "users AS u")

            val result = parser.parseQuery(split)

            val source = result.from[0] as SourceClause.Table
            assertThat(source.name).isEqualTo("users")
            assertThat(source.alias).isEqualTo("u")
        }

        @Test
        fun `parse table with alias without AS`() {
            val split = createSplit(from = "users u")

            val result = parser.parseQuery(split)

            val source = result.from[0] as SourceClause.Table
            assertThat(source.name).isEqualTo("users")
            assertThat(source.alias).isEqualTo("u")
        }

        @Test
        fun `parse table with schema`() {
            val split = createSplit(from = "public.users")

            val result = parser.parseQuery(split)

            val source = result.from[0] as SourceClause.Table
            assertThat(source.schema).isEqualTo("public")
            assertThat(source.name).isEqualTo("users")
        }

        @Test
        fun `parse table with schema and alias`() {
            val split = createSplit(from = "public.users AS u")

            val result = parser.parseQuery(split)

            val source = result.from[0] as SourceClause.Table
            assertThat(source.schema).isEqualTo("public")
            assertThat(source.name).isEqualTo("users")
            assertThat(source.alias).isEqualTo("u")
        }

        @Test
        fun `parse multiple tables - implicit join`() {
            val split = createSplit(from = "users, orders, products")

            val result = parser.parseQuery(split)

            assertThat(result.from).hasSize(3)
            assertThat((result.from[0] as SourceClause.Table).name).isEqualTo("users")
            assertThat((result.from[1] as SourceClause.Table).name).isEqualTo("orders")
            assertThat((result.from[2] as SourceClause.Table).name).isEqualTo("products")
        }

        @Test
        fun `parse multiple tables with aliases`() {
            val split = createSplit(from = "users u, orders o")

            val result = parser.parseQuery(split)

            assertThat(result.from).hasSize(2)
            assertThat((result.from[0] as SourceClause.Table).alias).isEqualTo("u")
            assertThat((result.from[1] as SourceClause.Table).alias).isEqualTo("o")
        }

        @Test
        fun `parse subquery in from`() {
            val split = createSplit(from = "(SELECT * FROM users WHERE active = true) AS active_users")

            val result = parser.parseQuery(split)

            assertThat(result.from).hasSize(1)
            val source = result.from[0] as SourceClause.SubQuery
            assertThat(source.alias).isEqualTo("active_users")
            assertThat(source.query).isNotNull
        }

        @Test
        fun `parse subquery with nested subquery`() {
            val split = createSplit(
                from = "(SELECT * FROM users WHERE id IN (SELECT user_id FROM premium)) AS p"
            )

            val result = parser.parseQuery(split)

            val source = result.from[0] as SourceClause.SubQuery
            assertThat(source.alias).isEqualTo("p")
        }
    }

    @Nested
    @DisplayName("JOIN parsing")
    inner class JoinParsingTests {

        @Test
        fun `parse simple JOIN`() {
            val split = createSplit(join = "JOIN orders ON users.id = orders.user_id")

            val result = parser.parseQuery(split)

            assertThat(result.joins).hasSize(1)
            assertThat(result.joins[0].joinType).isEqualTo(JoinType.INNER)
        }

        @Test
        fun `parse INNER JOIN`() {
            val split = createSplit(join = "INNER JOIN orders ON users.id = orders.user_id")

            val result = parser.parseQuery(split)

            assertThat(result.joins[0].joinType).isEqualTo(JoinType.INNER)
        }

        @Test
        fun `parse LEFT JOIN`() {
            val split = createSplit(join = "LEFT JOIN orders ON users.id = orders.user_id")

            val result = parser.parseQuery(split)

            assertThat(result.joins[0].joinType).isEqualTo(JoinType.LEFT)
        }

        @Test
        fun `parse LEFT OUTER JOIN`() {
            val split = createSplit(join = "LEFT OUTER JOIN orders ON users.id = orders.user_id")

            val result = parser.parseQuery(split)

            assertThat(result.joins[0].joinType).isEqualTo(JoinType.LEFT)
        }

        @Test
        fun `parse RIGHT JOIN`() {
            val split = createSplit(join = "RIGHT JOIN orders ON users.id = orders.user_id")

            val result = parser.parseQuery(split)

            assertThat(result.joins[0].joinType).isEqualTo(JoinType.RIGHT)
        }

        @Test
        fun `parse FULL JOIN`() {
            val split = createSplit(join = "FULL JOIN orders ON users.id = orders.user_id")

            val result = parser.parseQuery(split)

            assertThat(result.joins[0].joinType).isEqualTo(JoinType.FULL)
        }

        @Test
        fun `parse CROSS JOIN`() {
            val split = createSplit(join = "CROSS JOIN orders")

            val result = parser.parseQuery(split)

            assertThat(result.joins[0].joinType).isEqualTo(JoinType.CROSS)
            assertThat(result.joins[0].conditions).isEmpty()
        }

        @Test
        fun `parse join with table alias`() {
            val split = createSplit(join = "LEFT JOIN orders o ON u.id = o.user_id")

            val result = parser.parseQuery(split)

            val source = result.joins[0].source as SourceClause.Table
            assertThat(source.name).isEqualTo("orders")
            assertThat(source.alias).isEqualTo("o")
        }

        @Test
        fun `parse join condition with column references`() {
            val split = createSplit(join = "JOIN orders ON users.id = orders.user_id")

            val result = parser.parseQuery(split)

            val condition = result.joins[0].conditions[0]
            assertThat(condition.leftOperand.table).isEqualTo("users")
            assertThat(condition.leftOperand.column).isEqualTo("id")
            assertThat(condition.operator).isEqualTo(ComparisonOperator.EQUALS)

            val rightOp = condition.rightOperand as JoinOperand.Column
            assertThat(rightOp.table).isEqualTo("orders")
            assertThat(rightOp.name).isEqualTo("user_id")
        }

        @Test
        fun `parse join condition with value`() {
            val split = createSplit(join = "JOIN orders ON orders.status = 'active'")

            val result = parser.parseQuery(split)

            val condition = result.joins[0].conditions[0]
            val rightOp = condition.rightOperand as JoinOperand.Value
            assertThat(rightOp.sqlValue).isEqualTo("'active'")
        }

        @Test
        fun `parse multiple join conditions with AND`() {
            val split = createSplit(
                join = "JOIN orders ON users.id = orders.user_id AND orders.status = 'active'"
            )

            val result = parser.parseQuery(split)

            assertThat(result.joins[0].conditions).hasSize(2)
            assertThat(result.joins[0].conditions[0].logicalOperator).isEqualTo(LogicalOperator.AND)
        }

        @Test
        fun `parse multiple joins`() {
            val split = createSplit(
                join = "LEFT JOIN orders o ON u.id = o.user_id INNER JOIN payments p ON o.id = p.order_id"
            )

            val result = parser.parseQuery(split)

            assertThat(result.joins).hasSize(2)
            assertThat(result.joins[0].joinType).isEqualTo(JoinType.LEFT)
            assertThat(result.joins[1].joinType).isEqualTo(JoinType.INNER)
        }

        @Test
        fun `parse join with subquery`() {
            val split = createSplit(
                join = "LEFT JOIN (SELECT user_id, SUM(amount) AS total FROM orders GROUP BY user_id) o ON u.id = o.user_id"
            )

            val result = parser.parseQuery(split)

            val source = result.joins[0].source as SourceClause.SubQuery
            assertThat(source.alias).isEqualTo("o")
            assertThat(source.query).isNotNull
        }
    }

    @Nested
    @DisplayName("WHERE parsing")
    inner class WhereParsingTests {

        @Test
        fun `parse simple equals condition`() {
            val split = createSplit(where = "id = 1")

            val result = parser.parseQuery(split)

            assertThat(result.whereClauses).hasSize(1)
            val clause = result.whereClauses[0]
            assertThat((clause.leftOperand as WhereOperand.Column).name).isEqualTo("id")
            assertThat(clause.operator).isEqualTo(ComparisonOperator.EQUALS)
            assertThat((clause.rightOperand as WhereOperand.Value).sqlValue).isEqualTo("1")
        }

        @Test
        fun `parse string value condition`() {
            val split = createSplit(where = "status = 'active'")

            val result = parser.parseQuery(split)

            val clause = result.whereClauses[0]
            assertThat((clause.rightOperand as WhereOperand.Value).sqlValue).isEqualTo("'active'")
        }

        @Test
        fun `parse not equals condition`() {
            val split = createSplit(where = "status != 'deleted'")

            val result = parser.parseQuery(split)

            assertThat(result.whereClauses[0].operator).isEqualTo(ComparisonOperator.NOT_EQUALS)
        }

        @Test
        fun `parse greater than condition`() {
            val split = createSplit(where = "age > 18")

            val result = parser.parseQuery(split)

            assertThat(result.whereClauses[0].operator).isEqualTo(ComparisonOperator.GREATER_THAN)
        }

        @Test
        fun `parse less than condition`() {
            val split = createSplit(where = "price < 100")

            val result = parser.parseQuery(split)

            assertThat(result.whereClauses[0].operator).isEqualTo(ComparisonOperator.LESS_THAN)
        }

        @Test
        fun `parse greater or equals condition`() {
            val split = createSplit(where = "amount >= 50")

            val result = parser.parseQuery(split)

            assertThat(result.whereClauses[0].operator).isEqualTo(ComparisonOperator.GREATER_OR_EQUALS)
        }

        @Test
        fun `parse less or equals condition`() {
            val split = createSplit(where = "quantity <= 10")

            val result = parser.parseQuery(split)

            assertThat(result.whereClauses[0].operator).isEqualTo(ComparisonOperator.LESS_OR_EQUALS)
        }

        @Test
        fun `parse column with table prefix`() {
            val split = createSplit(where = "users.status = 'active'")

            val result = parser.parseQuery(split)

            val leftOp = result.whereClauses[0].leftOperand as WhereOperand.Column
            assertThat(leftOp.table).isEqualTo("users")
            assertThat(leftOp.name).isEqualTo("status")
        }

        @Test
        fun `parse LIKE condition`() {
            val split = createSplit(where = "name LIKE '%john%'")

            val result = parser.parseQuery(split)

            assertThat(result.whereClauses[0].operator).isEqualTo(ComparisonOperator.LIKE)
            assertThat((result.whereClauses[0].rightOperand as WhereOperand.Value).sqlValue).isEqualTo("'%john%'")
        }

        @Test
        fun `parse NOT LIKE condition`() {
            val split = createSplit(where = "name NOT LIKE '%test%'")

            val result = parser.parseQuery(split)

            assertThat(result.whereClauses[0].operator).isEqualTo(ComparisonOperator.NOT_LIKE)
        }

        @Test
        fun `parse IN condition with values`() {
            val split = createSplit(where = "status IN ('active', 'pending')")

            val result = parser.parseQuery(split)

            assertThat(result.whereClauses[0].operator).isEqualTo(ComparisonOperator.IN)
            val rightOp = result.whereClauses[0].rightOperand as WhereOperand.ValueList
            assertThat(rightOp.values).containsExactly("'active'", "'pending'")
        }

        @Test
        fun `parse IN condition with numbers`() {
            val split = createSplit(where = "id IN (1, 2, 3)")

            val result = parser.parseQuery(split)

            val rightOp = result.whereClauses[0].rightOperand as WhereOperand.ValueList
            assertThat(rightOp.values).containsExactly("1", "2", "3")
        }

        @Test
        fun `parse NOT IN condition`() {
            val split = createSplit(where = "id NOT IN (1, 2, 3)")

            val result = parser.parseQuery(split)

            assertThat(result.whereClauses[0].operator).isEqualTo(ComparisonOperator.NOT_IN)
        }

        @Test
        fun `parse IN with subquery`() {
            val split = createSplit(where = "id IN (SELECT user_id FROM premium_users)")

            val result = parser.parseQuery(split)

            assertThat(result.whereClauses[0].operator).isEqualTo(ComparisonOperator.IN)
            val rightOp = result.whereClauses[0].rightOperand as WhereOperand.SubQuery
            assertThat(rightOp.query).isNotNull
        }

        @Test
        fun `parse BETWEEN condition`() {
            val split = createSplit(where = "age BETWEEN 18 AND 65")

            val result = parser.parseQuery(split)

            assertThat(result.whereClauses[0].operator).isEqualTo(ComparisonOperator.BETWEEN)
            val rightOp = result.whereClauses[0].rightOperand as WhereOperand.Range
            assertThat(rightOp.from).isEqualTo("18")
            assertThat(rightOp.to).isEqualTo("65")
        }

        @Test
        fun `parse IS NULL condition`() {
            val split = createSplit(where = "deleted_at IS NULL")

            val result = parser.parseQuery(split)

            assertThat(result.whereClauses[0].operator).isEqualTo(ComparisonOperator.IS_NULL)
            assertThat(result.whereClauses[0].rightOperand).isNull()
        }

        @Test
        fun `parse IS NOT NULL condition`() {
            val split = createSplit(where = "email IS NOT NULL")

            val result = parser.parseQuery(split)

            assertThat(result.whereClauses[0].operator).isEqualTo(ComparisonOperator.IS_NOT_NULL)
        }

        @Test
        fun `parse multiple conditions with AND`() {
            val split = createSplit(where = "status = 'active' AND age > 18")

            val result = parser.parseQuery(split)

            assertThat(result.whereClauses).hasSize(2)
            assertThat(result.whereClauses[0].logicalOperator).isEqualTo(LogicalOperator.AND)
            assertThat(result.whereClauses[1].logicalOperator).isNull()
        }

        @Test
        fun `parse multiple conditions with OR`() {
            val split = createSplit(where = "status = 'active' OR status = 'pending'")

            val result = parser.parseQuery(split)

            assertThat(result.whereClauses).hasSize(2)
            assertThat(result.whereClauses[0].logicalOperator).isEqualTo(LogicalOperator.OR)
        }

        @Test
        fun `parse mixed AND and OR conditions`() {
            val split = createSplit(where = "a = 1 AND b = 2 OR c = 3")

            val result = parser.parseQuery(split)

            assertThat(result.whereClauses).hasSize(3)
            assertThat(result.whereClauses[0].logicalOperator).isEqualTo(LogicalOperator.AND)
            assertThat(result.whereClauses[1].logicalOperator).isEqualTo(LogicalOperator.OR)
        }

        @Test
        fun `parse NOT condition`() {
            val split = createSplit(where = "NOT active = true")

            val result = parser.parseQuery(split)

            assertThat(result.whereClauses[0].isNegated).isTrue()
        }

        @Test
        fun `parse BETWEEN with AND should not split on BETWEEN's AND`() {
            val split = createSplit(where = "age BETWEEN 18 AND 65 AND status = 'active'")

            val result = parser.parseQuery(split)

            assertThat(result.whereClauses).hasSize(2)
            assertThat(result.whereClauses[0].operator).isEqualTo(ComparisonOperator.BETWEEN)
            assertThat(result.whereClauses[1].operator).isEqualTo(ComparisonOperator.EQUALS)
        }
    }

    @Nested
    @DisplayName("GROUP BY parsing")
    inner class GroupByParsingTests {

        @Test
        fun `parse single column`() {
            val split = createSplit(groups = "status")

            val result = parser.parseQuery(split)

            assertThat(result.groupByColumns).hasSize(1)
            assertThat(result.groupByColumns[0].column).isEqualTo("status")
            assertThat(result.groupByColumns[0].table).isNull()
        }

        @Test
        fun `parse column with table prefix`() {
            val split = createSplit(groups = "users.status")

            val result = parser.parseQuery(split)

            assertThat(result.groupByColumns[0].table).isEqualTo("users")
            assertThat(result.groupByColumns[0].column).isEqualTo("status")
        }

        @Test
        fun `parse multiple columns`() {
            val split = createSplit(groups = "status, role, department")

            val result = parser.parseQuery(split)

            assertThat(result.groupByColumns).hasSize(3)
            assertThat(result.groupByColumns[0].column).isEqualTo("status")
            assertThat(result.groupByColumns[1].column).isEqualTo("role")
            assertThat(result.groupByColumns[2].column).isEqualTo("department")
        }

        @Test
        fun `parse multiple columns with table prefixes`() {
            val split = createSplit(groups = "u.status, u.role")

            val result = parser.parseQuery(split)

            assertThat(result.groupByColumns).hasSize(2)
            assertThat(result.groupByColumns[0].table).isEqualTo("u")
            assertThat(result.groupByColumns[1].table).isEqualTo("u")
        }
    }

    @Nested
    @DisplayName("HAVING parsing")
    inner class HavingParsingTests {

        @Test
        fun `parse simple having condition`() {
            val split = createSplit(having = "COUNT(*) > 5")

            val result = parser.parseQuery(split)

            assertThat(result.havingClauses).hasSize(1)
            val clause = result.havingClauses[0]
            assertThat(clause.leftOperand).isInstanceOf(WhereOperand.Function::class.java)
            assertThat(clause.operator).isEqualTo(ComparisonOperator.GREATER_THAN)
        }

        @Test
        fun `parse having with SUM`() {
            val split = createSplit(having = "SUM(amount) >= 1000")

            val result = parser.parseQuery(split)

            val leftOp = result.havingClauses[0].leftOperand as WhereOperand.Function
            assertThat(leftOp.functionType).isEqualTo(AggregateFunctionType.SUM)
            assertThat(leftOp.column).isEqualTo("amount")
        }

        @Test
        fun `parse having with multiple conditions`() {
            val split = createSplit(having = "COUNT(*) > 5 AND SUM(amount) > 1000")

            val result = parser.parseQuery(split)

            assertThat(result.havingClauses).hasSize(2)
        }
    }

    @Nested
    @DisplayName("ORDER BY parsing")
    inner class OrderByParsingTests {

        @Test
        fun `parse single column ascending by default`() {
            val split = createSplit(sort = "name")

            val result = parser.parseQuery(split)

            assertThat(result.sortColumns).hasSize(1)
            assertThat(result.sortColumns[0].column).isEqualTo("name")
            assertThat(result.sortColumns[0].direction).isEqualTo(SortDirection.ASC)
        }

        @Test
        fun `parse column with explicit ASC`() {
            val split = createSplit(sort = "name ASC")

            val result = parser.parseQuery(split)

            assertThat(result.sortColumns[0].direction).isEqualTo(SortDirection.ASC)
        }

        @Test
        fun `parse column with DESC`() {
            val split = createSplit(sort = "created_at DESC")

            val result = parser.parseQuery(split)

            assertThat(result.sortColumns[0].direction).isEqualTo(SortDirection.DESC)
        }

        @Test
        fun `parse column with table prefix`() {
            val split = createSplit(sort = "users.name DESC")

            val result = parser.parseQuery(split)

            assertThat(result.sortColumns[0].table).isEqualTo("users")
            assertThat(result.sortColumns[0].column).isEqualTo("name")
        }

        @Test
        fun `parse multiple columns`() {
            val split = createSplit(sort = "status ASC, created_at DESC")

            val result = parser.parseQuery(split)

            assertThat(result.sortColumns).hasSize(2)
            assertThat(result.sortColumns[0].column).isEqualTo("status")
            assertThat(result.sortColumns[0].direction).isEqualTo(SortDirection.ASC)
            assertThat(result.sortColumns[1].column).isEqualTo("created_at")
            assertThat(result.sortColumns[1].direction).isEqualTo(SortDirection.DESC)
        }

        @Test
        fun `parse NULLS FIRST`() {
            val split = createSplit(sort = "name ASC NULLS FIRST")

            val result = parser.parseQuery(split)

            assertThat(result.sortColumns[0].nullsPosition).isEqualTo(NullsPosition.FIRST)
        }

        @Test
        fun `parse NULLS LAST`() {
            val split = createSplit(sort = "name DESC NULLS LAST")

            val result = parser.parseQuery(split)

            assertThat(result.sortColumns[0].nullsPosition).isEqualTo(NullsPosition.LAST)
        }
    }

    @Nested
    @DisplayName("LIMIT and OFFSET")
    inner class LimitOffsetTests {

        @Test
        fun `parse limit`() {
            val split = createSplit(limit = 10)

            val result = parser.parseQuery(split)

            assertThat(result.limit).isEqualTo(10)
        }

        @Test
        fun `parse offset`() {
            val split = createSplit(offset = 20)

            val result = parser.parseQuery(split)

            assertThat(result.offset).isEqualTo(20)
        }

        @Test
        fun `parse limit and offset together`() {
            val split = createSplit(limit = 10, offset = 20)

            val result = parser.parseQuery(split)

            assertThat(result.limit).isEqualTo(10)
            assertThat(result.offset).isEqualTo(20)
        }
    }

    @Nested
    @DisplayName("Complex queries")
    inner class ComplexQueryTests {

        @Test
        fun `parse full featured query`() {
            val split = SplitSqlQuery(
                select = "u.name, COUNT(o.id) AS order_count, SUM(o.amount) AS total",
                from = "users u",
                join = "LEFT JOIN orders o ON u.id = o.user_id AND o.status = 'completed'",
                where = "u.active = true AND u.created_at > '2024-01-01'",
                groups = "u.name",
                having = "COUNT(o.id) > 5",
                sort = "total DESC",
                limit = 10,
                offset = 0
            )

            val result = parser.parseQuery(split)

            assertThat(result.columns).hasSize(3)

            assertThat(result.from).hasSize(1)
            assertThat((result.from[0] as SourceClause.Table).alias).isEqualTo("u")

            assertThat(result.joins).hasSize(1)
            assertThat(result.joins[0].joinType).isEqualTo(JoinType.LEFT)
            assertThat(result.joins[0].conditions).hasSize(2)

            assertThat(result.whereClauses).hasSize(2)

            assertThat(result.groupByColumns).hasSize(1)

            assertThat(result.havingClauses).hasSize(1)

            assertThat(result.sortColumns).hasSize(1)
            assertThat(result.sortColumns[0].direction).isEqualTo(SortDirection.DESC)

            assertThat(result.limit).isEqualTo(10)
            assertThat(result.offset).isEqualTo(0)
        }

        @Test
        fun `parse query with multiple subqueries`() {
            val split = SplitSqlQuery(
                select = "u.name, (SELECT COUNT(*) FROM orders WHERE user_id = u.id) AS orders",
                from = "(SELECT * FROM users WHERE active = true) u",
                join = null,
                where = "u.id IN (SELECT user_id FROM premium_users)",
                groups = null,
                having = null,
                sort = null,
                limit = null,
                offset = null
            )

            val result = parser.parseQuery(split)

            assertThat(result.columns[1].expression).isInstanceOf(SelectExpression.SubQuery::class.java)

            assertThat(result.from[0]).isInstanceOf(SourceClause.SubQuery::class.java)

            assertThat(result.whereClauses[0].rightOperand).isInstanceOf(WhereOperand.SubQuery::class.java)
        }
    }


    @Nested
    @DisplayName("Validation")
    inner class ValidationTests {

        @Test
        fun `validate returns error for empty columns`() {
            val split = createSplit(select = "")

            val result = parser.parseQuery(split)
            val errors = parser.validateParsedQuery(result)

            assertThat(errors).contains("SELECT clause is empty")
        }

        @Test
        fun `validate returns error for empty from`() {
            val split = SplitSqlQuery(select = "*", from = "")

            val result = parser.parseQuery(split)
            val errors = parser.validateParsedQuery(result)

            assertThat(errors).contains("FROM clause is empty")
        }

        @Test
        fun `validate returns no errors for valid query`() {
            val split = createSplit()

            val result = parser.parseQuery(split)
            val errors = parser.validateParsedQuery(result)

            assertThat(errors).isEmpty()
        }
    }

    private fun createSplit(
        select: String = "*",
        from: String = "users",
        join: String? = null,
        where: String? = null,
        groups: String? = null,
        having: String? = null,
        sort: String? = null,
        limit: Int? = null,
        offset: Int? = null
    ) = SplitSqlQuery(
        select = select,
        from = from,
        join = join,
        where = where,
        groups = groups,
        having = having,
        sort = sort,
        limit = limit,
        offset = offset
    )
}