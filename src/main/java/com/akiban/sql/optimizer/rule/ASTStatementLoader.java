/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */

package com.akiban.sql.optimizer.rule;

import com.akiban.server.expression.std.Comparison;
import com.akiban.server.types.AkType;
import com.akiban.server.types3.TPreptimeValue;
import com.akiban.server.types3.Types3Switch;
import com.akiban.server.types3.mcompat.mtypes.MString;
import com.akiban.server.types3.pvalue.PValue;
import com.akiban.sql.optimizer.plan.*;
import com.akiban.sql.optimizer.plan.JoinNode;
import com.akiban.sql.optimizer.plan.JoinNode.JoinType;
import com.akiban.sql.optimizer.plan.ResultSet.ResultField;
import com.akiban.sql.optimizer.plan.Sort.OrderByExpression;
import com.akiban.sql.optimizer.plan.UpdateStatement.UpdateColumn;
import static com.akiban.sql.optimizer.rule.PlanContext.*;

import com.akiban.sql.optimizer.*;

import com.akiban.sql.StandardException;
import com.akiban.sql.parser.*;
import com.akiban.sql.types.DataTypeDescriptor;
import com.akiban.sql.types.TypeId;

import com.akiban.ais.model.Column;
import com.akiban.ais.model.Group;
import com.akiban.ais.model.Routine;
import com.akiban.ais.model.UserTable;

import com.akiban.server.error.InsertWrongCountException;
import com.akiban.server.error.NoSuchTableException;
import com.akiban.server.error.ProtectedTableDDLException;
import com.akiban.server.error.SQLParserInternalException;
import com.akiban.server.error.UnsupportedSQLException;
import com.akiban.server.error.OrderGroupByNonIntegerConstant;
import com.akiban.server.error.OrderGroupByIntegerOutOfRange;
import com.akiban.server.error.WrongExpressionArityException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/** Turn a parsed SQL AST into this package's format.
 */
public class ASTStatementLoader extends BaseRule
{
    // TODO: Maybe move this into a separate class.
    public static final int IN_TO_OR_MAX_COUNT_DEFAULT = 1;

    private static final Logger logger = LoggerFactory.getLogger(ASTStatementLoader.class);

    @Override
    protected Logger getLogger() {
        return logger;
    }

    public static final WhiteboardMarker<AST> MARKER = 
        new DefaultWhiteboardMarker<>();

    /** Recover the {@link AST} put on the whiteboard when loaded. */
    public static AST getAST(PlanContext plan) {
        return plan.getWhiteboard(MARKER);
    }

    @Override
    public void apply(PlanContext plan) {
        AST ast = (AST)plan.getPlan();
        plan.putWhiteboard(MARKER, ast);
        DMLStatementNode stmt = ast.getStatement();
        try {
            plan.setPlan(new Loader((SchemaRulesContext)plan.getRulesContext()).toStatement(stmt));
        }
        catch (StandardException ex) {
            throw new SQLParserInternalException(ex);
        }
    }

    static class Loader {
        private SchemaRulesContext rulesContext;

        Loader(SchemaRulesContext rulesContext) {
            this.rulesContext = rulesContext;
            pushEquivalenceFinder();
        }

        private void pushEquivalenceFinder() {
            columnEquivalences.push(new EquivalenceFinder<ColumnExpression>() {
                @Override
                protected String describeElement(ColumnExpression element) {
                    return element.getSQLsource().getTableName() + "." + element.getColumn().getName();
                }
            });
        }

        private void popEquivalenceFinder() {
            columnEquivalences.pop();
        }
        
        private EquivalenceFinder<ColumnExpression> peekEquivalenceFinder() {
            return columnEquivalences.element();
        }

        /** Convert given statement into appropriate intermediate form. */
        protected BaseStatement toStatement(DMLStatementNode stmt) throws StandardException {
            switch (stmt.getNodeType()) {
            case NodeTypes.CURSOR_NODE:
                return toSelectQuery((CursorNode)stmt);
            case NodeTypes.DELETE_NODE:
                return toDeleteStatement((DeleteNode)stmt);
            case NodeTypes.UPDATE_NODE:
                return toUpdateStatement((UpdateNode)stmt);
            case NodeTypes.INSERT_NODE:
                return toInsertStatement((InsertNode)stmt);
            default:
                throw new StandardException("Unsupported statement type: " +
                                            stmt.statementToString());
            }
        }

        // SELECT
        protected SelectQuery toSelectQuery(CursorNode cursorNode) 
                throws StandardException {
            PlanNode query = toQueryForSelect(cursorNode.getResultSetNode(),
                                              cursorNode.getOrderByList(),
                                              cursorNode.getOffsetClause(),
                                              cursorNode.getFetchFirstClause());
            if (cursorNode.getUpdateMode() == CursorNode.UpdateMode.UPDATE)
                throw new UnsupportedSQLException("FOR UPDATE", cursorNode);
            return new SelectQuery(query, peekEquivalenceFinder());
        }

        // UPDATE
        protected DMLStatement toUpdateStatement(UpdateNode updateNode)
                throws StandardException {
            ResultSetNode rs = updateNode.getResultSetNode();
            PlanNode query = toQuery((SelectNode)rs);
            TableNode targetTable = getTargetTable(updateNode);
            TableSource selectTable = getTargetTableSource(updateNode);
            assert (selectTable.getTable() == targetTable);
            ResultColumnList rcl = rs.getResultColumns();
            List<UpdateColumn> updateColumns = 
                new ArrayList<>(rcl.size());
            for (ResultColumn result : rcl) {
                Column column = getColumnReferenceColumn(result.getReference(),
                                                         "result column");
                ExpressionNode value = toExpression(result.getExpression());
                updateColumns.add(new UpdateColumn(column, value));
            }
            ReturningValues values = calculateReturningValues(updateNode.getReturningList(),
                                                              (FromTable)updateNode.getUserData());
            query = new UpdateStatement(query, targetTable, 
                                        updateColumns, values.table); 

            if (values.row != null) {
                query = new Project(query, values.row);
            }
            return new DMLStatement(query, BaseUpdateStatement.StatementType.UPDATE, 
                                    selectTable, targetTable, 
                                    values.results, values.table,
                                    peekEquivalenceFinder());
        }

        // INSERT
        protected DMLStatement toInsertStatement(InsertNode insertNode)
                throws StandardException {
            PlanNode query = toQueryForSelect(insertNode.getResultSetNode(),
                                              insertNode.getOrderByList(),
                                              insertNode.getOffset(),
                                              insertNode.getFetchFirst());
            if (query instanceof ResultSet)
                query = ((ResultSet)query).getInput();
            TableNode targetTable = getTargetTable(insertNode);
            ResultColumnList rcl = insertNode.getTargetColumnList();
            int ncols = insertNode.getResultSetNode().getResultColumns().size();
            List<Column> targetColumns;
            if (rcl != null) {
                if (ncols != rcl.size())
                    throw new InsertWrongCountException(rcl.size(), ncols);
                targetColumns = new ArrayList<>(rcl.size());
                for (ResultColumn resultColumn : rcl) {
                    Column column = getColumnReferenceColumn(resultColumn.getReference(),
                                                             "Unsupported target column");
                    targetColumns.add(column);
                }
            }
            else {
                // No explicit column list: use DDL order.
                List<Column> aisColumns = targetTable.getTable().getColumns();
                if (ncols > aisColumns.size())
                    throw new InsertWrongCountException(aisColumns.size(), ncols);
                targetColumns = new ArrayList<>(ncols);
                for (int i = 0; i < ncols; i++) {
                    targetColumns.add(aisColumns.get(i));
                }
            }

            ReturningValues values = calculateReturningValues(insertNode.getReturningList(),
                                                              (FromTable)insertNode.getUserData());
            
            query = new InsertStatement(query, targetTable, 
                                        targetColumns, values.table);
            if (values.row != null) {
                query = new Project(query, values.row);
            }
            return new DMLStatement(query, BaseUpdateStatement.StatementType.INSERT, 
                                    null, targetTable,
                                    values.results, values.table, 
                                    peekEquivalenceFinder());
        }
    
        
        // DELETE
        protected DMLStatement toDeleteStatement(DeleteNode deleteNode)
                throws StandardException {
            PlanNode query = toQuery((SelectNode)deleteNode.getResultSetNode());
            TableNode targetTable = getTargetTable(deleteNode);
            TableSource selectTable = getTargetTableSource(deleteNode);
            assert (selectTable.getTable() == targetTable);
            
            ReturningValues values = calculateReturningValues (deleteNode.getReturningList(), 
                                                               (FromTable)deleteNode.getUserData());
            
            query = new DeleteStatement(query, targetTable, values.table);
            if (values.row != null) {
                query = new Project (query, values.row);
            }
            return new DMLStatement(query, BaseUpdateStatement.StatementType.DELETE, 
                                    selectTable, targetTable, 
                                    values.results, values.table, 
                                    peekEquivalenceFinder());
        }
        
        private class ReturningValues {
            public List<ExpressionNode> row = null;
            public TableSource table = null;
            public List<ResultField> results = null;
        }

        protected ReturningValues calculateReturningValues(ResultColumnList rcl, FromTable table)
                throws StandardException {
            ReturningValues values = new ReturningValues();
            if (rcl != null) {
                values.table = (TableSource)toJoinNode(table, true);
                values.row = new ArrayList<>(rcl.size());
                for (ResultColumn resultColumn : rcl) {
                    values.row.add(toExpression(resultColumn.getExpression()));
                }
                values.results = resultColumns(rcl);
            }
            return values;
        }
        
        /** The query part of SELECT / INSERT, which might be VALUES / UNION */
        protected PlanNode toQueryForSelect(ResultSetNode resultSet,
                                            OrderByList orderByList,
                                            ValueNode offsetClause,
                                            ValueNode fetchFirstClause)
                throws StandardException {
            if (resultSet instanceof SelectNode)
                return toQueryForSelect((SelectNode)resultSet,
                                        orderByList,
                                        offsetClause,
                                        fetchFirstClause);
            else if (resultSet instanceof RowResultSetNode) {
                List<ExpressionNode> row = toExpressionsRow(resultSet);
                List<List<ExpressionNode>> rows = new ArrayList<>(1);
                rows.add(row);
                return new ExpressionsSource(rows);
            }
            else if (resultSet instanceof RowsResultSetNode) {
                List<List<ExpressionNode>> rows = new ArrayList<>();
                for (ResultSetNode row : ((RowsResultSetNode)resultSet).getRows()) {
                    rows.add(toExpressionsRow(row));
                }
                return new ExpressionsSource(rows);
            }
            else if (resultSet instanceof UnionNode) {
                UnionNode union = (UnionNode)resultSet;
                boolean all = union.isAll();
                PlanNode left = toQueryForSelect(union.getLeftResultSet());
                PlanNode right = toQueryForSelect(union.getRightResultSet());
                return new Union(left, right, all);
            }
            else
                throw new UnsupportedSQLException("Unsupported query", resultSet);
        }

        protected List<ResultField> resultColumns(ResultColumnList rcl) 
                        throws StandardException {
            List<ResultField> results = new ArrayList<>(rcl.size());
            for (ResultColumn result : rcl) {
                String name = result.getName();
                DataTypeDescriptor type = result.getType();
                boolean nameDefaulted =
                    (result.getExpression() instanceof ColumnReference) &&
                    (name == ((ColumnReference)result.getExpression()).getColumnName());
                Column column = null;
                ExpressionNode expr = toExpression(result.getExpression());
                if (expr instanceof ColumnExpression) {
                    column = ((ColumnExpression)expr).getColumn();
                    if ((column != null) && nameDefaulted)
                        name = column.getName();
                }
                results.add(new ResultField(name, type, column));
            }
            return results;
        }
        /** A normal SELECT */
        protected PlanNode toQueryForSelect(SelectNode selectNode,
                                            OrderByList orderByList,
                                            ValueNode offsetClause,
                                            ValueNode fetchFirstClause)
                throws StandardException {
            PlanNode query = toQuery(selectNode);

            ResultColumnList rcl = selectNode.getResultColumns();
            List<ResultField> results = resultColumns (rcl);
            List<ExpressionNode> projects = new ArrayList<>(rcl.size());

            for (ResultColumn result : rcl) {
                ExpressionNode expr = toExpression(result.getExpression());
                projects.add(expr);
            }

            List<OrderByExpression> sorts = new ArrayList<>();
            if (orderByList != null) {
                for (OrderByColumn orderByColumn : orderByList) {
                    ExpressionNode expression = toOrderGroupBy(orderByColumn.getExpression(), projects, "ORDER");
                    sorts.add(new OrderByExpression(expression,
                                                    orderByColumn.isAscending()));
                }
            }

            // Stupid but legal: 
            //  SELECT 1 FROM t ORDER BY MAX(c); 
            //  SELECT 1 FROM t HAVING MAX(c) > 0; 
            if ((selectNode.getGroupByList() != null) ||
                (selectNode.getHavingClause() != null) ||
                hasAggregateFunction(projects) ||
                hasAggregateFunctionA(sorts)) {
                query = toAggregateSource(query, selectNode.getGroupByList(), projects);
                query = new Select(query, toConditions(selectNode.getHavingClause(), projects));
            }

            if (selectNode.hasWindows())
                throw new UnsupportedSQLException("WINDOW", selectNode);
        
            do_distinct:
            {
                // Distinct puts the Project before any Sort so as to only do one sort.
                if (selectNode.isDistinct()) {
                    Project project = new Project(query, projects);
                    if (sorts.isEmpty()) {
                        query = new Distinct(project);
                        break do_distinct;
                    }
                    else if (adjustSortsForDistinct(sorts, project)) {
                        query = new Sort(project, sorts);
                        query = new Distinct(query, Distinct.Implementation.EXPLICIT_SORT);
                        break do_distinct;
                    }
                    else {
                        query = new AggregateSource(query, new ArrayList<>((projects)));
                        // Don't break: treat like non-distinct case.
                    }
                }
                if (!sorts.isEmpty()) {
                    query = new Sort(query, sorts);
                }
                query = new Project(query, projects);
            }

            if ((offsetClause != null) || 
                (fetchFirstClause != null))
                query = toLimit(query, offsetClause, fetchFirstClause);

            query = new ResultSet(query, results);

            return query;
        }

        protected PlanNode toQueryForSelect(ResultSetNode resultSet)
                throws StandardException {
            return toQueryForSelect(resultSet, null, null, null);
        }

        protected List<ExpressionNode> toExpressionsRow(ResultSetNode resultSet)
                throws StandardException {
            ResultColumnList resultColumns = resultSet.getResultColumns();
            List<ExpressionNode> row = new ArrayList<>(resultColumns.size());
            for (ResultColumn resultColumn : resultColumns) {
                row.add(toExpression(resultColumn.getExpression()));
            }
            return row;
        }

        /** The common top-level join and select part of all statements. */
        protected PlanNode toQuery(SelectNode selectNode)
                throws StandardException {
            PlanNode input;
            if (!selectNode.getFromList().isEmpty()) {
                Joinable joins = null;
                for (FromTable fromTable : selectNode.getFromList()) {
                    if (joins == null)
                        joins = toJoinNode(fromTable, true);
                    else
                        joins = joinNodes(joins, toJoinNode(fromTable, true), JoinType.INNER);
                }
                input = joins;
            }
            else {
                // No FROM list means one row with presumably constant Projects.
                input = new ExpressionsSource(Collections.singletonList(Collections.<ExpressionNode>emptyList()));
            }
            ConditionList conditions = toConditions(selectNode.getWhereClause());
            if (hasAggregateFunction(conditions))
                throw new UnsupportedSQLException("Aggregate not allowed in WHERE",
                                                  selectNode.getWhereClause());
            return new Select(input, conditions);
        }

        protected Map<FromTable,Joinable> joinNodes =
            new HashMap<>();

        protected Joinable toJoinNode(FromTable fromTable, boolean required)
                throws StandardException {
            Joinable result;
            if (fromTable instanceof FromBaseTable) {
                TableBinding tb = (TableBinding)fromTable.getUserData();
                if (tb == null)
                    throw new UnsupportedSQLException("FROM table",
                                                      fromTable);
                UserTable userTable = (UserTable)tb.getTable();
                TableNode table = getTableNode(userTable);
                String name = fromTable.getCorrelationName();
                if (name == null) {
                    if (userTable.getName().getSchemaName().equals(rulesContext.getDefaultSchemaName()))
                        name = userTable.getName().getTableName();
                    else
                        name = userTable.getName().toString();
                }
                result = new TableSource(table, required, name);
            }
            else if (fromTable instanceof com.akiban.sql.parser.JoinNode) {
                com.akiban.sql.parser.JoinNode joinNode = 
                    (com.akiban.sql.parser.JoinNode)fromTable;
                JoinType joinType;
                switch (joinNode.getNodeType()) {
                case NodeTypes.JOIN_NODE:
                    joinType = JoinType.INNER;
                    break;
                case NodeTypes.HALF_OUTER_JOIN_NODE:
                    if (((HalfOuterJoinNode)joinNode).isRightOuterJoin())
                        joinType = JoinType.RIGHT;
                    else
                        joinType = JoinType.LEFT;
                    break;
                default:
                    throw new UnsupportedSQLException("Unsupported join type", joinNode);
                }
                JoinNode join = joinNodes(toJoinNode((FromTable)joinNode.getLeftResultSet(),
                                                     required && (joinType != JoinType.RIGHT)),
                                          toJoinNode((FromTable)joinNode.getRightResultSet(),
                                                     required && (joinType != JoinType.LEFT)),
                                          joinType);
                join.setJoinConditions(toConditions(joinNode.getJoinClause()));
                result = join;
            }
            else if (fromTable instanceof FromSubquery) {
                FromSubquery fromSubquery = (FromSubquery)fromTable;
                PlanNode subquery = toQueryForSelect(fromSubquery.getSubquery(),
                                                     fromSubquery.getOrderByList(),
                                                     fromSubquery.getOffset(),
                                                     fromSubquery.getFetchFirst());
                result = new SubquerySource(new Subquery(subquery, peekEquivalenceFinder()),
                                            fromSubquery.getExposedName());
            }
            else
                throw new UnsupportedSQLException("Unsupported FROM non-table", fromTable);
            joinNodes.put(fromTable, result);
            return result;
        }

        protected JoinNode joinNodes(Joinable left, Joinable right, JoinType joinType)
                throws StandardException {
            return new JoinNode(left, right, joinType);
        }

        /** Add a set of conditions to input. */
        protected ConditionList toConditions(ValueNode cnfClause)
                throws StandardException {
            return toConditions(cnfClause, null);
        }

        protected ConditionList toConditions(ValueNode cnfClause,
                                             List<ExpressionNode> projects)
                throws StandardException {
            ConditionList conditions = new ConditionList();
            while (cnfClause != null) {
                if (cnfClause.isBooleanTrue()) break;
                if (!(cnfClause instanceof AndNode))
                    throw new UnsupportedSQLException("Unsupported complex WHERE",
                                                      cnfClause);
                AndNode andNode = (AndNode)cnfClause;
                cnfClause = andNode.getRightOperand();
                ValueNode condition = andNode.getLeftOperand();
                addCondition(conditions, condition, projects);
            }
            return conditions;
        }

        /** Fill the given list with conditions from given AST node.
         * Takes a list because BETWEEN generates <em>two</em> conditions.
         */
        protected void addCondition(List<ConditionExpression> conditions, 
                                    ValueNode condition,
                                    List<ExpressionNode> projects)
                throws StandardException {
            DataTypeDescriptor conditionType = null;
            switch (condition.getNodeType()) {
            case NodeTypes.BINARY_EQUALS_OPERATOR_NODE:
                addComparisonCondition(conditions, projects,
                                       (BinaryOperatorNode)condition, Comparison.EQ);
                return;
            case NodeTypes.BINARY_GREATER_THAN_OPERATOR_NODE:
                addComparisonCondition(conditions, projects,
                                       (BinaryOperatorNode)condition, Comparison.GT);
                return;
            case NodeTypes.BINARY_GREATER_EQUALS_OPERATOR_NODE:
                addComparisonCondition(conditions, projects,
                                       (BinaryOperatorNode)condition, Comparison.GE);
                return;
            case NodeTypes.BINARY_LESS_THAN_OPERATOR_NODE:
                addComparisonCondition(conditions, projects,
                                       (BinaryOperatorNode)condition, Comparison.LT);
                return;
            case NodeTypes.BINARY_LESS_EQUALS_OPERATOR_NODE:
                addComparisonCondition(conditions, projects,
                                       (BinaryOperatorNode)condition, Comparison.LE);
                return;
            case NodeTypes.BINARY_NOT_EQUALS_OPERATOR_NODE:
                addComparisonCondition(conditions, projects,
                                       (BinaryOperatorNode)condition, Comparison.NE);
                return;
            case NodeTypes.BETWEEN_OPERATOR_NODE:
                addBetweenCondition(conditions, projects,
                                    (BetweenOperatorNode)condition);
                return;
            case NodeTypes.IN_LIST_OPERATOR_NODE:
                addInCondition(conditions, projects,
                               (InListOperatorNode)condition);
                return;
            case NodeTypes.SUBQUERY_NODE:
                addSubqueryCondition(conditions, projects,
                                     (SubqueryNode)condition);
                return;
            case NodeTypes.LIKE_OPERATOR_NODE:
                addFunctionCondition(conditions, projects,
                                     (TernaryOperatorNode)condition);
                return;
            case NodeTypes.IS_NULL_NODE:
            case NodeTypes.IS_NOT_NULL_NODE:
                addIsNullCondition(conditions, projects,
                                   (IsNullNode)condition);
                return;
            case NodeTypes.IS_NODE:
                addIsCondition(conditions, projects,
                               (IsNode)condition);
                return;
            case NodeTypes.OR_NODE:
            case NodeTypes.AND_NODE:
            case NodeTypes.NOT_NODE:
                addLogicalFunctionCondition(conditions, projects, condition);
                return;
            case NodeTypes.BOOLEAN_CONSTANT_NODE:
                conditions.add(new BooleanConstantExpression(((BooleanConstantNode)condition).getBooleanValue()));
                return;
            case NodeTypes.UNTYPED_NULL_CONSTANT_NODE:
                conditions.add(new BooleanConstantExpression(null));
                return;
            case NodeTypes.PARAMETER_NODE:
                conditionType = condition.getType();
                if (conditionType == null) {
                    conditionType = new DataTypeDescriptor(TypeId.BOOLEAN_ID, true);
                    condition.setType(conditionType);
                }
                conditions.add(new ParameterCondition(((ParameterNode)condition)
                                                      .getParameterNumber(),
                                                      conditionType, condition));
                return;
            case NodeTypes.CAST_NODE:
                // Use given cast type if it's suitable for a condition.
                conditionType = condition.getType();
                // CAST inside to BOOLEAN below.
                condition = ((CastNode)condition).getCastOperand();
                break;
            case NodeTypes.JAVA_TO_SQL_VALUE_NODE:
                conditions.add((ConditionExpression)
                               toExpression(((JavaToSQLValueNode)condition).getJavaValueNode(), 
                                            condition, true,
                                            projects));
                return;
            }
            // Anything else gets CAST to BOOLEAN, which may fail
            // later due to lack of a suitable cast.
            if (conditionType == null)
                conditionType = condition.getType();
            if (conditionType == null)
                conditionType = new DataTypeDescriptor(TypeId.BOOLEAN_ID, true);
            else if (!conditionType.getTypeId().isBooleanTypeId())
                    conditionType = new DataTypeDescriptor(TypeId.BOOLEAN_ID, conditionType.isNullable());
            conditions.add(new BooleanCastExpression(toExpression(condition, projects),
                                                     conditionType, condition));
        }

        protected void addComparisonCondition(List<ConditionExpression> conditions,
                                              List<ExpressionNode> projects,
                                              BinaryOperatorNode binop, Comparison op)
                throws StandardException {
            ExpressionNode left = toExpression(binop.getLeftOperand(), projects);
            ExpressionNode right = toExpression(binop.getRightOperand(), projects);
            conditions.add(new ComparisonCondition(op, left, right,
                                                   binop.getType(), binop));
        }

        protected void addBetweenCondition(List<ConditionExpression> conditions,
                                           List<ExpressionNode> projects,
                                           BetweenOperatorNode between)
                throws StandardException {
            ExpressionNode left = toExpression(between.getLeftOperand(), projects);
            ValueNodeList rightOperandList = between.getRightOperandList();
            ExpressionNode right1 = toExpression(rightOperandList.get(0), projects);
            ExpressionNode right2 = toExpression(rightOperandList.get(1), projects);
            DataTypeDescriptor type = between.getType();
            conditions.add(new ComparisonCondition(Comparison.GE, left, right1, type, null));
            conditions.add(new ComparisonCondition(Comparison.LE, left, right2, type, null));
        }
        
        protected void addInCondition(List<ConditionExpression> conditions,
                                      List<ExpressionNode> projects,
                                      InListOperatorNode in) 
                throws StandardException
        {
            RowConstructorNode lhs = in.getLeftOperand();
            RowConstructorNode rhs = in.getRightOperandList();
            ValueNodeList leftOperandList = lhs.getNodeList();
            ValueNodeList rightOperandList = rhs.getNodeList();
            ConditionExpression inCondition;
            if (rightOperandList.size() <= getInToOrMaxCount()) {
                inCondition = buildInConditionNested(in, projects);
            }
            else {
                List<List<ExpressionNode>> rows = new ArrayList<>();
                for (ValueNode rightOperand : rightOperandList) {
                    List<ExpressionNode> row = new ArrayList<>(1);
                    flattenInSameShape(row, rightOperand, lhs, projects);
                    rows.add(row);
                }
                ExpressionsSource source = new ExpressionsSource(rows);
                List<ConditionExpression> conds = new ArrayList<>();
                flattenAnyComparisons(conds, leftOperandList, source, projects, in.getType());
                ConditionExpression combined = null;
                for (ConditionExpression cond : conds) {
                    combined = andConditions(combined, cond);
                }
                List<ExpressionNode> fields = new ArrayList<>(1);
                fields.add(combined);
                PlanNode subquery = new Project(source, fields);
                inCondition = new AnyCondition(new Subquery(subquery, peekEquivalenceFinder()), in.getType(), in);
            }
            if (in.isNegated()) {
                inCondition = negateCondition(inCondition, in);
            }
            conditions.add(inCondition);
        }

        protected ConditionExpression getEqual(InListOperatorNode in, 
                                               List<ExpressionNode> projects,
                                               ValueNode left, ValueNode right) throws StandardException
        {
            if (right instanceof RowConstructorNode)
            {
                if (left instanceof RowConstructorNode)
                {
                    ValueNodeList leftList = ((RowConstructorNode)left).getNodeList();
                    ValueNodeList rightList = ((RowConstructorNode)right).getNodeList();
                    
                    if (leftList.size() != rightList.size())
                        throw new IllegalArgumentException("mismatched columns count in IN " 
                                + "left : " + leftList.size() + ", right: " + rightList.size());
                    
                    ConditionExpression result = null;
                    for (int n = 0; n < leftList.size(); ++n)
                    {
                        ConditionExpression equalNode = getEqual(in,
                                                       projects,
                                                       leftList.get(n), rightList.get(n));
                        result = andConditions(result, equalNode);
                    }
                    return result;
                }
                else
                    throw new IllegalArgumentException("mismatchec column count in IN");
            }
            else
            {
                if (left instanceof RowConstructorNode) {
                    ValueNodeList leftList = ((RowConstructorNode)left).getNodeList();
                    if (leftList.size() != 1)
                        throw new IllegalArgumentException("mismatch columns count in IN");
                    left = leftList.get(0);
                }
                
                ExpressionNode rightExp = toExpression(right, projects);
                ExpressionNode leftExp = toExpression(left, projects);
                
                return new ComparisonCondition(Comparison.EQ, 
                                               leftExp, rightExp,
                                               in.getType(), in);
            }
        }
        
        protected ConditionExpression buildInConditionNested(InListOperatorNode in,
                                                             List<ExpressionNode> projects) throws StandardException
        {
            RowConstructorNode leftRow = in.getLeftOperand();
            RowConstructorNode rightRow = in.getRightOperandList();
            
            ConditionExpression result = null;

            for (ValueNode rightNode : rightRow.getNodeList())
            {
                ConditionExpression equalNode = getEqual(in, projects, leftRow, rightNode);

                if (result == null)
                    result = equalNode;
                else
                {
                    List<ConditionExpression> operands = new ArrayList<>(2);

                    operands.add(result);
                    operands.add(equalNode);

                    result = new LogicalFunctionCondition("or", operands, in.getType(), in);
                }
            }
            return result;
        }
        
        private void flattenInSameShape(List<ExpressionNode> row,
                                        ValueNode rightOperand, ValueNode leftOperand,
                                        List<ExpressionNode> projects)
                throws StandardException {
            if (rightOperand instanceof RowConstructorNode) {
                if (!(leftOperand instanceof RowConstructorNode))
                    throw new IllegalArgumentException("Row value given where single expected");
                ValueNodeList leftList = ((RowConstructorNode)leftOperand).getNodeList();
                ValueNodeList rightList = ((RowConstructorNode)rightOperand).getNodeList();
                if (leftList.size() != rightList.size())
                    throw new IllegalArgumentException("mismatched columns count in IN " 
                                                       + "left : " + leftList.size() + ", right: " + rightList.size());
                for (int i = 0; i < leftList.size(); i++) {
                    flattenInSameShape(row, rightList.get(i), leftList.get(i), projects);
                }
            }
            else {
                if ((leftOperand instanceof RowConstructorNode) &&
                    (((RowConstructorNode)leftOperand).getNodeList().size() != 1))
                    throw new IllegalArgumentException("Single value given where row expected");
                row.add(toExpression(rightOperand, projects));
            }
        }


        private void flattenAnyComparisons(List<ConditionExpression> conds, ValueNodeList leftOperandList, ExpressionsSource source, 
                                           List<ExpressionNode> projects, DataTypeDescriptor sqlType) throws StandardException {
            for (ValueNode leftOperand : leftOperandList) {
                if (leftOperand instanceof RowConstructorNode) {
                    flattenAnyComparisons(conds, ((RowConstructorNode)leftOperand).getNodeList(), source,
                                          projects, sqlType);
                }
                else {
                    ExpressionNode left = toExpression(leftOperand, projects);
                    ConditionExpression cond = 
                        new ComparisonCondition(Comparison.EQ,
                                                left,
                                                new ColumnExpression(source, conds.size(), left.getSQLtype(), null),
                                                sqlType, null);
                    conds.add(cond);
                }
            }
        }

        protected void addSubqueryCondition(List<ConditionExpression> conditions, 
                                            List<ExpressionNode> projects,
                                            SubqueryNode subqueryNode)
                throws StandardException {
            PlanNode subquery = toQueryForSelect(subqueryNode.getResultSet(),
                                                 subqueryNode.getOrderByList(),
                                                 subqueryNode.getOffset(),
                                                 subqueryNode.getFetchFirst());
            if (subquery instanceof ResultSet)
                subquery = ((ResultSet)subquery).getInput();
            boolean negate = false;
            Comparison comp = Comparison.EQ;
            List<ExpressionNode> operands = null;
            ExpressionNode operand = null;
            boolean needOperand = false, multipleOperands = false;
            //ConditionList innerConds = null;
            switch (subqueryNode.getSubqueryType()) {
            case EXISTS:
                break;
            case NOT_EXISTS:
                negate = true;
                break;
            case IN:
                multipleOperands = true;
                /* falls through */
            case EQ_ANY: 
                needOperand = true;
                break;
            case EQ_ALL: 
                negate = true;
                comp = Comparison.NE;
                needOperand = true;
                break;
            case NE_ANY: 
                comp = Comparison.NE;
                needOperand = true;
                break;
            case NOT_IN: 
                multipleOperands = true;
                /* falls through */
            case NE_ALL: 
                negate = true;
                needOperand = true;
                break;
            case GT_ANY: 
                comp = Comparison.GT;
                needOperand = true;
                break;
            case GT_ALL: 
                negate = true;
                comp = Comparison.LE;
                needOperand = true;
                break;
            case GE_ANY: 
                comp = Comparison.GE;
                needOperand = true;
                break;
            case GE_ALL: 
                negate = true;
                comp = Comparison.LT;
                needOperand = true;
                break;
            case LT_ANY: 
                comp = Comparison.LT;
                needOperand = true;
                break;
            case LT_ALL: 
                negate = true;
                comp = Comparison.GE;
                needOperand = true;
                break;
            case LE_ANY: 
                comp = Comparison.LE;
                needOperand = true;
                break;
            case LE_ALL: 
                negate = true;
                comp = Comparison.GT;
                needOperand = true;
                break;
            case FROM:
            default:
                assert false;
            }
            boolean distinct = false;
            // TODO: This may not be right for c IN (SELECT x ... UNION SELECT y ...).
            // Maybe turn that into an OR and optimize each separately.
            {
                PlanWithInput prev = null;
                PlanNode plan = subquery;
                while (true) {
                    if (!(plan instanceof BasePlanWithInput))
                        break;
                    PlanNode next = ((BasePlanWithInput)plan).getInput();
                    if (plan instanceof Project) {
                        Project project = (Project)plan;
                        if (needOperand) {
                            operands = project.getFields();
                            if (!multipleOperands && (operands.size() != 1))
                                throw new UnsupportedSQLException("Subquery must have exactly one column", subqueryNode);
                            operand = project.getFields().get(0);
                        }
                        // Don't need project any more.
                        if (prev != null)
                            prev.replaceInput(plan, next);
                        else
                            subquery = next;
                        break;
                    }
                    if (plan instanceof Distinct) {
                        distinct = true;
                    }
                    else {
                        prev = (PlanWithInput)plan;
                    }
                    plan = next;
                }
            }
            ConditionExpression condition;
            if (needOperand) {
                assert (operand != null);
                ValueNode leftOperand = subqueryNode.getLeftOperand();
                ConditionExpression inner = null;
                if (multipleOperands) {
                    if (leftOperand instanceof RowConstructorNode) {
                        ValueNodeList leftOperands = ((RowConstructorNode)leftOperand).getNodeList();
                        if (operands.size() != leftOperands.size())
                            throw new IllegalArgumentException("mismatched columns count in IN " 
                                                               + "left : " + leftOperands.size() + ", right: " + operands.size());
                        for (int i = 0; i < leftOperands.size(); i++) {
                            ExpressionNode left = toExpression(leftOperands.get(i)
, projects);
                            ConditionExpression cond = new ComparisonCondition(comp, left, operands.get(i),
                                                                               subqueryNode.getType(), null);
                            inner = andConditions(inner, cond);
                        }
                    }
                    else {
                        if (operands.size() != 1)
                            throw new IllegalArgumentException("Subquery must have exactly one column");
                        multipleOperands = false;
                    }
                }
                if (!multipleOperands) {
                    ExpressionNode left = toExpression(leftOperand, projects);
                    inner = new ComparisonCondition(comp, left, operand,
                                                    subqueryNode.getType(), 
                                                    subqueryNode);
                }
                // We take this condition back off from the top of the
                // physical plan and move it to the expression, but it's
                // easier to think about the scoping as evaluated at the
                // end of the inner query.
                List<ExpressionNode> fields = new ArrayList<>(1);
                fields.add(inner);
                subquery = new Project(subquery, fields);
                if (distinct)
                    // See InConditionReverser#convert(Select,AnyCondition).
                    subquery = new Distinct(subquery);
                condition = new AnyCondition(new Subquery(subquery, peekEquivalenceFinder()),
                                             subqueryNode.getType(), subqueryNode);
            }
            else {
                condition = new ExistsCondition(new Subquery(subquery, peekEquivalenceFinder()),
                                                subqueryNode.getType(), subqueryNode);
            }
            if (negate) {
                condition = negateCondition(condition, subqueryNode);
            }
            conditions.add(condition);
        }

        protected void addFunctionCondition(List<ConditionExpression> conditions,
                                            List<ExpressionNode> projects,
                                            UnaryOperatorNode unary)
                throws StandardException {
            List<ExpressionNode> operands = new ArrayList<>(1);
            operands.add(toExpression(unary.getOperand(), projects));
            conditions.add(new FunctionCondition(unary.getMethodName(),
                                                 operands,
                                                 unary.getType(), unary));
        }

        protected void addFunctionCondition(List<ConditionExpression> conditions,
                                            List<ExpressionNode> projects,
                                            BinaryOperatorNode binary)
                throws StandardException {
            List<ExpressionNode> operands = new ArrayList<>(2);
            operands.add(toExpression(binary.getLeftOperand(), projects));
            operands.add(toExpression(binary.getRightOperand(), projects));
            conditions.add(new FunctionCondition(binary.getMethodName(),
                                                 operands,
                                                 binary.getType(), binary));
        }

        protected void addFunctionCondition(List<ConditionExpression> conditions,
                                            List<ExpressionNode> projects,
                                            TernaryOperatorNode ternary)
                throws StandardException {
            List<ExpressionNode> operands = new ArrayList<>(3);
            operands.add(toExpression(ternary.getReceiver(), projects));
            operands.add(toExpression(ternary.getLeftOperand(), projects));
            
            ValueNode third = ternary.getRightOperand();
            if (third != null)
                operands.add(toExpression(third, projects));

            conditions.add(new FunctionCondition(ternary.getMethodName(),
                                                 operands,
                                                 ternary.getType(), ternary));
        }

        protected void addIsNullCondition(List<ConditionExpression> conditions,
                                          List<ExpressionNode> projects,
                                          IsNullNode isNull)
                throws StandardException {
            List<ExpressionNode> operands = new ArrayList<>(1);
            operands.add(toExpression(isNull.getOperand(), projects));
            String function = isNull.getMethodName();
            boolean negated = false;
            if ("isNotNull".equals(function)) {
                function = "isNull";
                negated = true;
            }
            ConditionExpression cond = new FunctionCondition(function, operands,
                                                             isNull.getType(), isNull);
            if (negated) {
                cond = negateCondition(cond, isNull);
            }
            conditions.add(cond);
        }

        protected void addIsCondition(List<ConditionExpression> conditions,
                                      List<ExpressionNode> projects,
                                      IsNode is)
                throws StandardException {
            List<ExpressionNode> operands = new ArrayList<>(1);
            operands.add(toCondition(is.getLeftOperand(), projects));
            String function;
            Boolean value = (Boolean)((ConstantNode)is.getRightOperand()).getValue();
            if (value == null)
                function = "isUnknown";
            else if (value.booleanValue())
                function = "isTrue";
            else
                function = "isFalse";
            ConditionExpression cond = new FunctionCondition(function, operands,
                                                             is.getType(), is);
            if (is.isNegated()) {
                cond = negateCondition(cond, is);
            }
            conditions.add(cond);
        }

        protected void addLogicalFunctionCondition(List<ConditionExpression> conditions, 
                                                   List<ExpressionNode> projects,
                                                   ValueNode condition) 
                throws StandardException {
            String functionName;
            List<ConditionExpression> operands = null;
            if (condition instanceof UnaryLogicalOperatorNode) {
                switch (condition.getNodeType()) {
                case NodeTypes.NOT_NODE:
                    functionName = "not";
                    break;
                default:
                    throw new UnsupportedSQLException("Unsuported condition", condition);
                }
                operands = new ArrayList<>(1);
                operands.add(toCondition(((UnaryLogicalOperatorNode)condition).getOperand(),
                                         projects));
            }
            else if (condition instanceof BinaryLogicalOperatorNode) {
                ValueNode leftOperand = ((BinaryLogicalOperatorNode)
                                         condition).getLeftOperand();
                ValueNode rightOperand = ((BinaryLogicalOperatorNode)
                                          condition).getRightOperand();
                switch (condition.getNodeType()) {
                case NodeTypes.AND_NODE:
                    // Can just fold straight into conjunction.
                    addCondition(conditions, leftOperand, projects);
                    if (!rightOperand.isBooleanTrue())
                        addCondition(conditions, rightOperand, projects);
                    return;
                case NodeTypes.OR_NODE:
                    if (rightOperand.isBooleanFalse()) {
                        addCondition(conditions, leftOperand, projects);
                        return;
                    }
                    functionName = "or";
                    break;
                default:
                    throw new UnsupportedSQLException("Unsuported condition", condition);
                }
                operands = new ArrayList<>(2);
                operands.add(toCondition(leftOperand, projects));
                operands.add(toCondition(rightOperand, projects));
            }
            else
                throw new UnsupportedSQLException("Unsuported condition", condition);
            conditions.add(new LogicalFunctionCondition(functionName, operands,
                                                        condition.getType(), condition));
        }

        /** Is this a boolean condition used as a normal value? */
        protected boolean isConditionExpression(ValueNode value)
                throws StandardException {
            switch (value.getNodeType()) {
            case NodeTypes.BINARY_EQUALS_OPERATOR_NODE:
            case NodeTypes.BINARY_GREATER_THAN_OPERATOR_NODE:
            case NodeTypes.BINARY_GREATER_EQUALS_OPERATOR_NODE:
            case NodeTypes.BINARY_LESS_THAN_OPERATOR_NODE:
            case NodeTypes.BINARY_LESS_EQUALS_OPERATOR_NODE:
            case NodeTypes.BINARY_NOT_EQUALS_OPERATOR_NODE:
            case NodeTypes.BETWEEN_OPERATOR_NODE:
            case NodeTypes.IN_LIST_OPERATOR_NODE:
            case NodeTypes.LIKE_OPERATOR_NODE:
            case NodeTypes.IS_NULL_NODE:
            case NodeTypes.IS_NOT_NULL_NODE:
            case NodeTypes.IS_NODE:
            case NodeTypes.OR_NODE:
            case NodeTypes.AND_NODE:
            case NodeTypes.NOT_NODE:
                return true;
            case NodeTypes.SUBQUERY_NODE:
                return (((SubqueryNode)value).getSubqueryType() != 
                        SubqueryNode.SubqueryType.EXPRESSION);
            default:
                return false;
            }
        }

        /** Get given condition as a single node. */
        protected ConditionExpression toCondition(ValueNode condition,
                                                  List<ExpressionNode> projects)
                throws StandardException {
            List<ConditionExpression> conditions = new ArrayList<>(1);
            addCondition(conditions, condition, projects);
            switch (conditions.size()) {
            case 0:
                return new BooleanConstantExpression(Boolean.TRUE);
            case 1:
                return conditions.get(0);
            case 2:
                // CASE WHEN x BETWEEN a AND b means multiple conditions from single one in AST.
                return new LogicalFunctionCondition("and", conditions,
                                                    condition.getType(), condition);
            default:
                {
                    // Make calls to binary AND function.
                    ConditionExpression rhs = null;
                    for (ConditionExpression lhs : conditions) {
                        rhs = andConditions(rhs, lhs);
                    }
                    return rhs;
                }
            }
        }

        /** Combine AND of conditions (or <code>null</code>) with another one. */
        protected ConditionExpression andConditions(ConditionExpression conds,
                                                    ConditionExpression cond) {
            if (conds == null)
                return cond;
            else {
                List<ConditionExpression> operands = new ArrayList<>(2);
                
                operands.add(conds);
                operands.add(cond);
                return new LogicalFunctionCondition("and", operands,
                                                    cond.getSQLtype(), null);
            }
        }

        /** Negate boolean condition. */
        protected ConditionExpression negateCondition(ConditionExpression cond,
                                                      ValueNode sql) {
            List<ConditionExpression> operands = new ArrayList<>(1);
            operands.add(cond);
            return new LogicalFunctionCondition("not", operands, sql.getType(), sql);
        }

        /** SELECT DISTINCT with sorting sorts by an input Project and
         * adds extra columns so as to only sort once for both
         * Distinct and the requested ordering.
         * Returns <code>false</code> if this is not possible and
         * DISTINCT should be turned into GROUP BY.
         */
        protected boolean adjustSortsForDistinct(List<OrderByExpression> sorts,
                                                 Project project)
                throws StandardException {
            List<ExpressionNode> exprs = project.getFields();
            BitSet used = new BitSet(exprs.size());
            for (OrderByExpression orderBy : sorts) {
                ExpressionNode expr = orderBy.getExpression();
                int idx = exprs.indexOf(expr);
                if (idx < 0) {
                    if (isDistinctSortNotSelectGroupBy())
                        return false;
                    throw new UnsupportedSQLException("SELECT DISTINCT requires that ORDER BY expressions be in the select list",
                                                      expr.getSQLsource());
                }
                ExpressionNode cexpr = new ColumnExpression(project, idx,
                                                            expr.getSQLtype(),
                                                            expr.getSQLsource());
                orderBy.setExpression(cexpr);
                used.set(idx);
            }
            for (int i = 0; i < exprs.size(); i++) {
                if (!used.get(i)) {
                    ExpressionNode expr = exprs.get(i);
                    ExpressionNode cexpr = new ColumnExpression(project, i,
                                                                expr.getSQLtype(),
                                                                expr.getSQLsource());
                    OrderByExpression orderBy = new OrderByExpression(cexpr,
                                                                      sorts.get(0).isAscending());
                    sorts.add(orderBy);
                }
            }
            return true;
        }

        private Boolean distinctSortNotSelectGroupBySetting = null;

        protected boolean isDistinctSortNotSelectGroupBy() {
            if (distinctSortNotSelectGroupBySetting == null)
                distinctSortNotSelectGroupBySetting = Boolean.valueOf(rulesContext.getProperty("distinctSortNotSelectGroupBy", "false"));
            return distinctSortNotSelectGroupBySetting;
        }

        /** LIMIT / OFFSET */
        protected Limit toLimit(PlanNode input, 
                                ValueNode offsetClause, 
                                ValueNode limitClause)
                throws StandardException {
            int offset = 0, limit = -1;
            boolean offsetIsParameter = false, limitIsParameter = false;
            if (offsetClause != null) {
                if (offsetClause instanceof ParameterNode) {
                    offset = ((ParameterNode)offsetClause).getParameterNumber();
                    offsetIsParameter = true;
                }
                else {
                    offset = getIntegerConstant(offsetClause, 
                                                "OFFSET must be constant integer");
                    if (offset < 0)
                        throw new UnsupportedSQLException("OFFSET must not be negative", 
                                                          offsetClause);
                }
            }
            if (limitClause != null) {
                if (limitClause instanceof ParameterNode) {
                    limit = ((ParameterNode)limitClause).getParameterNumber();
                    limitIsParameter = true;
                }
                else {
                    limit = getIntegerConstant(limitClause, 
                                               "LIMIT must be constant integer");
                    if (limit < 0)
                        throw new UnsupportedSQLException("LIMIT must not be negative", 
                                                          limitClause);
                }
            }
            return new Limit(input, 
                             offset, offsetIsParameter,
                             limit, limitIsParameter);
        }

        protected TableNode getTargetTable(DMLModStatementNode statement)
                throws StandardException {
            TableName tableName = statement.getTargetTableName();
            UserTable table = (UserTable)tableName.getUserData();
            if (table == null)
                throw new NoSuchTableException(tableName.getSchemaName(), 
                                               tableName.getTableName());
            if (table.isAISTable()) { 
                throw new ProtectedTableDDLException (table.getName());
            }
            return getTableNode(table);
        }
    
        protected TableSource getTargetTableSource(DMLModStatementNode statement)
                throws StandardException {
            FromTable firstTable = ((SelectNode)statement.getResultSetNode()).getFromList().get(0);
            return (TableSource)joinNodes.get(firstTable);
        }

        protected Map<Group,TableTree> groups = new HashMap<>();
        protected Deque<EquivalenceFinder<ColumnExpression>> columnEquivalences
                = new ArrayDeque<>(1);

        protected TableNode getTableNode(UserTable table)
                throws StandardException {
            Group group = table.getGroup();
            TableTree tables = groups.get(group);
            if (tables == null) {
                tables = new TableTree();
                groups.put(group, tables);
            }
            return tables.addNode(table);
        }

        protected TableNode getColumnTableNode(Column column)
                throws StandardException {
            return getTableNode(column.getUserTable());
        }

        /** Translate expression to intermediate form. */
        protected ExpressionNode toExpression(ValueNode valueNode)
                throws StandardException {
            return toExpression(valueNode, null);
        }

        protected ExpressionNode toExpression(ValueNode valueNode,
                                              List<ExpressionNode> projects)
                throws StandardException {
            if (valueNode == null) {
                return new ConstantExpression(null, AkType.NULL);
            }
            DataTypeDescriptor type = valueNode.getType();
            if (valueNode instanceof ColumnReference) {
                ColumnBinding cb = (ColumnBinding)((ColumnReference)valueNode).getUserData();
                if (cb == null)
                    throw new UnsupportedSQLException("Unsupported column", valueNode);
                Joinable joinNode = joinNodes.get(cb.getFromTable());
                if ((joinNode == null) &&
                    (cb.getFromTable() == null) &&
                    (projects != null) &&
                    (cb.getResultColumn() != null)) {
                    // Alias: use result column expression.
                    return projects.get(cb.getResultColumn().getColumnPosition()-1);
                }
                if (!(joinNode instanceof ColumnSource))
                    throw new UnsupportedSQLException("Unsupported column", valueNode);
                Column column = cb.getColumn();
                if (column != null)
                    return new ColumnExpression(((TableSource)joinNode), column, 
                                                type, valueNode);
                else
                    return new ColumnExpression(((ColumnSource)joinNode), 
                                                cb.getFromTable().getResultColumns().indexOf(cb.getResultColumn()), 
                                                type, valueNode);
            }
            else if (valueNode instanceof ConstantNode) {
                if (valueNode instanceof BooleanConstantNode)
                    return new BooleanConstantExpression((Boolean)((ConstantNode)valueNode).getValue(), 
                                                         type, valueNode);
                else {
                    Object value = ((ConstantNode)valueNode).getValue();
                    if (value instanceof Integer) {
                        int ival = ((Integer)value).intValue();
                        if (Types3Switch.ON) {
                            if ((ival >= Byte.MIN_VALUE) && (ival <= Byte.MAX_VALUE))
                                value = new Byte((byte)ival);
                            else if ((ival >= Short.MIN_VALUE) && (ival <= Short.MAX_VALUE))
                                value = new Short((short)ival);
                            ExpressionNode constInt = new ConstantExpression(value, type, AkType.LONG, valueNode);
                            constInt.getPreptimeValue(); // So that toString() won't try to use that AkType with an unsupported types2 type.
                            return constInt;
                        }
                        else {
                            value = new Long(ival);
                        }
                    }
                    return new ConstantExpression(value, type, valueNode);
                }
            }
            else if (valueNode instanceof ParameterNode)
                return new ParameterExpression(((ParameterNode)valueNode)
                                               .getParameterNumber(),
                                               type, valueNode);
            else if (valueNode instanceof CastNode)
                return new CastExpression(toExpression(((CastNode)valueNode)
                                                       .getCastOperand(),
                                                       projects),
                                          type, valueNode);
            else if (valueNode instanceof AggregateNode) {
                AggregateNode aggregateNode = (AggregateNode)valueNode;
                String function = aggregateNode.getAggregateName();
                ExpressionNode operand = null;
                if ("COUNT(*)".equals(function)) {
                    function = "COUNT";
                }
                else {
                    operand = toExpression(aggregateNode.getOperand(), projects);
                    if (hasAggregateFunction(operand)) {
                        throw new UnsupportedSQLException("Cannot nest aggregate functions",
                                                          aggregateNode);
                    }
                }
                
                if (aggregateNode instanceof GroupConcatNode)
                {
                    GroupConcatNode groupConcat = (GroupConcatNode) aggregateNode;
                    List<OrderByExpression> sorts = null;
                    OrderByList orderByList = groupConcat.getOrderBy();
                    
                    if (orderByList != null)
                    {
                        sorts = new ArrayList<>();
                        for (OrderByColumn orderByColumn : orderByList)
                        {
                            ExpressionNode expression = toOrderGroupBy(orderByColumn.getExpression(), projects, "ORDER");
                            sorts.add(new OrderByExpression(expression,
                                                            orderByColumn.isAscending()));
                        }
                    }
                    
                    return new AggregateFunctionExpression(function,
                                                       operand,
                                                       aggregateNode.isDistinct(),
                                                       type, valueNode,
                                                       groupConcat.getSeparator(),
                                                       sorts);
                }
                else
                    return new AggregateFunctionExpression(function,
                                                           operand,
                                                           aggregateNode.isDistinct(),
                                                           type, valueNode,
                                                           null,
                                                           null);
            }
            else if (isConditionExpression(valueNode)) {
                return toCondition(valueNode, projects);
            }
            else if (valueNode instanceof UnaryOperatorNode) {
                UnaryOperatorNode unary = (UnaryOperatorNode)valueNode;
                List<ExpressionNode> operands = new ArrayList<>(1);
                operands.add(toExpression(unary.getOperand(), projects));
                return new FunctionExpression(unary.getMethodName(),
                                              operands,
                                              unary.getType(), unary);
            }
            else if (valueNode instanceof BinaryOperatorNode) {
                BinaryOperatorNode binary = (BinaryOperatorNode)valueNode;
                List<ExpressionNode> operands = new ArrayList<>(2);
                int nodeType = valueNode.getNodeType();
                switch (nodeType) {
                case NodeTypes.CONCATENATION_OPERATOR_NODE:
                    // Operator is binary but function is nary: collapse.
                    while (true) {
                        operands.add(toExpression(binary.getLeftOperand(), projects));
                        ValueNode right = binary.getRightOperand();
                        if (right.getNodeType() != nodeType) {
                            operands.add(toExpression(right, projects));
                            break;
                        }
                        binary = (BinaryOperatorNode)right;
                    }
                    break;
                default:
                    operands.add(toExpression(binary.getLeftOperand(), projects));
                    operands.add(toExpression(binary.getRightOperand(), projects));
                }
                return new FunctionExpression(binary.getMethodName(),
                                              operands,
                                              binary.getType(), binary);
            }
            else if (valueNode instanceof TernaryOperatorNode) {
                TernaryOperatorNode ternary = (TernaryOperatorNode)valueNode;
                List<ExpressionNode> operands = new ArrayList<>(3);
                operands.add(toExpression(ternary.getReceiver(), projects));
                operands.add(toExpression(ternary.getLeftOperand(), projects));
                
                // java null means not present
                ValueNode third = ternary.getRightOperand();
                if (third != null)
                    operands.add(toExpression(third, projects));

                return new FunctionExpression(ternary.getMethodName(),
                                              operands,
                                              ternary.getType(), ternary);
            }
            else if (valueNode instanceof CoalesceFunctionNode) {
                CoalesceFunctionNode coalesce = (CoalesceFunctionNode)valueNode;
                List<ExpressionNode> operands = new ArrayList<>();
                for (ValueNode value : coalesce.getArgumentsList()) {
                    operands.add(toExpression(value, projects));
                }
                return new FunctionExpression(coalesce.getFunctionName(),
                                              operands,
                                              coalesce.getType(), coalesce);
            }
            else if (valueNode instanceof SubqueryNode) {
                SubqueryNode subqueryNode = (SubqueryNode)valueNode;
                pushEquivalenceFinder();
                PlanNode subquerySelect = toQueryForSelect(subqueryNode.getResultSet(),
                                                           subqueryNode.getOrderByList(),
                                                           subqueryNode.getOffset(),
                                                           subqueryNode.getFetchFirst());
                Subquery subquery = new Subquery(subquerySelect, peekEquivalenceFinder());
                popEquivalenceFinder();
                if ((subqueryNode.getType() != null) &&
                    subqueryNode.getType().getTypeId().isRowMultiSet())
                    return new SubqueryResultSetExpression(subquery,
                                                           subqueryNode.getType(), 
                                                           subqueryNode);
                else
                    return new SubqueryValueExpression(subquery, 
                                                       subqueryNode.getType(), 
                                                       subqueryNode);
            }
            else if (valueNode instanceof JavaToSQLValueNode) {
                return toExpression(((JavaToSQLValueNode)valueNode).getJavaValueNode(),
                                    valueNode,
                                    false,
                                    projects);
            }
            else if (valueNode instanceof CurrentDatetimeOperatorNode) {
                String functionName = FunctionsTypeComputer.currentDatetimeFunctionName((CurrentDatetimeOperatorNode)valueNode);
                if (functionName == null)
                    throw new UnsupportedSQLException("Unsupported datetime function", valueNode);
                return new FunctionExpression(functionName,
                                              Collections.<ExpressionNode>emptyList(),
                                              valueNode.getType(), valueNode);
            }
            else if (valueNode instanceof SpecialFunctionNode) {
                String functionName = FunctionsTypeComputer.specialFunctionName((SpecialFunctionNode)valueNode);
                if (functionName == null)
                    throw new UnsupportedSQLException("Unsupported special function", valueNode);
                return new FunctionExpression(functionName,
                                              Collections.<ExpressionNode>emptyList(),
                                              valueNode.getType(), valueNode);
            }
            else if (valueNode instanceof ConditionalNode) {
                ConditionalNode cond = (ConditionalNode)valueNode;
                return new IfElseExpression(toConditions(cond.getTestCondition(), projects),
                                            toExpression(cond.getThenNode(), projects),
                                            toExpression(cond.getElseNode(), projects),
                                            cond.getType(), cond);
            }
            else if (valueNode instanceof NextSequenceNode) {
                NextSequenceNode seqNode = (NextSequenceNode)valueNode;
                List<ExpressionNode> params = new ArrayList<>(2);

                String schema = seqNode.getSequenceName().hasSchema() ? 
                        seqNode.getSequenceName().getSchemaName() :
                            rulesContext.getDefaultSchemaName();
                // Extract the (potential) schema name as the first parameter
                params.add(new ConstantExpression(
                        new TPreptimeValue(MString.VARCHAR.instance(schema.length(), false),
                                           new PValue(MString.varcharFor(schema), schema))));
                // Extract the schema name as the second parameter
                String sequence = seqNode.getSequenceName().getTableName();
                params.add(new ConstantExpression(
                        new TPreptimeValue(MString.VARCHAR.instance(sequence.length(), false),
                                           new PValue(MString.varcharFor(sequence), sequence))));
                
                return new FunctionExpression ("nextval", params,
                                                valueNode.getType(), valueNode);
            }
            else if (valueNode instanceof CurrentSequenceNode) {
                CurrentSequenceNode seqNode = (CurrentSequenceNode)valueNode;
                List<ExpressionNode> params = new ArrayList<>(2);

                String schema = seqNode.getSequenceName().hasSchema() ? 
                        seqNode.getSequenceName().getSchemaName() :
                            rulesContext.getDefaultSchemaName();
                // Extract the (potential) schema name as the first parameter
                params.add(new ConstantExpression(
                        new TPreptimeValue(MString.VARCHAR.instance(schema.length(), false),
                                           new PValue(MString.varcharFor(schema), schema))));
                // Extract the schema name as the second parameter
                String sequence = seqNode.getSequenceName().getTableName();
                params.add(new ConstantExpression(
                        new TPreptimeValue(MString.VARCHAR.instance(sequence.length(), false),
                                           new PValue(MString.varcharFor(sequence), sequence))));
                
                return new FunctionExpression ("currval", params,
                                                valueNode.getType(), valueNode);
            }
            else
                throw new UnsupportedSQLException("Unsupported operand", valueNode);
        }

        // TODO: Need to figure out return type.  Maybe better to have
        // done this earlier and bound to a known function and elided the
        // Java stuff then.
        protected ExpressionNode toExpression(JavaValueNode javaToSQL,
                                              ValueNode valueNode,
                                              boolean asCondition,
                                              List<ExpressionNode> projects)
                throws StandardException {
            if (javaToSQL instanceof MethodCallNode) {
                MethodCallNode methodCall = (MethodCallNode)javaToSQL;
                List<ExpressionNode> operands = new ArrayList<>();
                if (methodCall.getMethodParameters() != null) {
                    for (JavaValueNode javaValue : methodCall.getMethodParameters()) {
                        operands.add(toExpression(javaValue, null, false, projects));
                    }
                }
                Routine routine = (Routine)methodCall.getUserData();
                if (routine != null) {
                    if (asCondition)
                        return new RoutineCondition(routine, operands,
                                                    valueNode.getType(), valueNode);
                    else
                        return new RoutineExpression(routine, operands,
                                                     valueNode.getType(), valueNode);
                }
                if (asCondition)
                    return new FunctionCondition(methodCall.getMethodName(),
                                                 operands,
                                                 valueNode.getType(), valueNode);
                else if (AggregateFunctionExpression.class.getName().equals(methodCall.getJavaClassName())) {
                    if (operands.size() != 1)
                        throw new WrongExpressionArityException(2, operands.size());
                    return new AggregateFunctionExpression(methodCall.getMethodName(),
                                                           operands.get(0), false,
                                                           valueNode.getType(), valueNode,
                                                           null,  // *supposed* separator
                                                           null); // order by list
                }
                else
                    return new FunctionExpression(methodCall.getMethodName(),
                                                  operands,
                                                  valueNode.getType(), valueNode);
            }
            else if (javaToSQL instanceof SQLToJavaValueNode) {
                return toExpression(((SQLToJavaValueNode)javaToSQL).getSQLValueNode(),
                                    projects);
            }
            else 
                throw new UnsupportedSQLException("Unsupported operand", valueNode);
        }

        /** Get the column that this node references or else return null
         * or throw given error.
         */
        protected Column getColumnReferenceColumn(ValueNode value, String errmsg)
                throws StandardException {
            if (value instanceof ColumnReference) {
                ColumnReference cref = (ColumnReference)value;
                ColumnBinding cb = (ColumnBinding)cref.getUserData();
                if (cb != null) {
                    Column column = cb.getColumn();
                    if (column != null)
                        return column;
                }
            }
            if (errmsg == null)
                return null;
            throw new UnsupportedSQLException(errmsg, value);
        }

        /** Get the constant integer value that this node represents or else throw error. */
        protected int getIntegerConstant(ValueNode value, String errmsg) {
            if (value instanceof NumericConstantNode) {
                Object number = ((NumericConstantNode)value).getValue();
                if (number instanceof Integer)
                    return ((Integer)number).intValue();
            }
            throw new UnsupportedSQLException(errmsg, value);
        }

        /** Value for ORDER / GROUP BY.
         * Can be:<ul>
         * <li>ordinary expression</li>
         * <li>ordinal index into the projects</li>
         * <li>alias of one of the projects</li></ul>
         */
        protected ExpressionNode toOrderGroupBy(ValueNode valueNode,
                                                List<ExpressionNode> projects,
                                                String which)
                throws StandardException {
            ExpressionNode expression = toExpression(valueNode, projects);
            if (expression.isConstant()) {
                Object value = ((ConstantExpression)expression).getValue();
                if ((value instanceof Long) || (value instanceof Integer) ||
                    (value instanceof Short) || (value instanceof Byte)) {
                    int i = ((Number)value).intValue();
                    if ((i <= 0) || (i > projects.size()))
                        throw new OrderGroupByIntegerOutOfRange(which, i, projects.size());
                    expression = (ExpressionNode)projects.get(i-1);
                }
                else
                    throw new OrderGroupByNonIntegerConstant(which, expression.getSQLsource());
            }
            return expression;
        }

        /** Construct an aggregating node.
         * This only sets the skeleton with the group by fields. Later,
         * aggregate functions from the result columns, HAVING & ORDER BY
         * clauses will be added there and the result column adjusted to
         * reflect this.
         */
        protected AggregateSource toAggregateSource(PlanNode input,
                                                    GroupByList groupByList,
                                                    List<ExpressionNode> projects)
                throws StandardException {
            List<ExpressionNode> groupBy = new ArrayList<>();
            if (groupByList != null) {
                for (GroupByColumn groupByColumn : groupByList) {
                    groupBy.add(toOrderGroupBy(groupByColumn.getColumnExpression(),
                                               projects, "GROUP"));
                }
            }
            return new AggregateSource(input, groupBy);
        }
    
        /** Does any element include an aggregate function? */
        protected boolean hasAggregateFunction(Collection<? extends ExpressionNode> c) {
            for (ExpressionNode expr : c) {
                if (hasAggregateFunction(expr))
                    return true;
            }
            return false;
        }

        /** Does any element include an aggregate function? */
        protected boolean hasAggregateFunctionA(Collection<? extends AnnotatedExpression> c) {
            for (AnnotatedExpression aexpr : c) {
                if (hasAggregateFunction(aexpr.getExpression()))
                    return true;
            }
            return false;
        }

        /** Does this expression include any aggregates? */
        protected boolean hasAggregateFunction(ExpressionNode expr) {
            return HasAggregateFunction.of(expr);
        }

        public static class HasAggregateFunction implements ExpressionVisitor {
            private boolean found = false;

            @Override
            public boolean visitEnter(ExpressionNode n) {
                return visit(n);
            }
            @Override
            public boolean visitLeave(ExpressionNode n) {
                return !found;
            }
            @Override
            public boolean visit(ExpressionNode n) {
                if (n instanceof AggregateFunctionExpression) {
                    found = true;
                    return false;
                }
                return true;
            }

            static boolean of(ExpressionNode expr) {
                HasAggregateFunction haf = new HasAggregateFunction();
                expr.accept(haf);
                return haf.found;
            }
        }

        public int getInToOrMaxCount() {
            String prop = rulesContext.getProperty("inToOrMaxCount");
            if (prop != null)
                return Integer.valueOf(prop);
            else
                return IN_TO_OR_MAX_COUNT_DEFAULT;
        }

    }
}
