
package com.akiban.sql.optimizer.rule;

import com.akiban.ais.model.Routine;
import com.akiban.server.collation.AkCollator;
import com.akiban.qp.operator.API;
import com.akiban.qp.operator.Operator;
import com.akiban.qp.rowtype.RowType;
import com.akiban.server.expression.std.Comparison;
import com.akiban.server.expression.std.Expressions;
import com.akiban.server.expression.std.InExpression;
import com.akiban.sql.optimizer.TypesTranslation;
import com.akiban.sql.optimizer.plan.*;
import com.akiban.sql.script.ScriptBindingsRoutineExpression;
import com.akiban.sql.script.ScriptFunctionJavaRoutineExpression;
import com.akiban.sql.server.ServerJavaMethodExpression;
import com.akiban.sql.types.DataTypeDescriptor;
import com.akiban.sql.types.TypeId;

import com.akiban.server.expression.Expression;
import com.akiban.server.expression.ExpressionEvaluation;
import com.akiban.server.expression.ExpressionType;
import com.akiban.server.expression.std.ExpressionTypes;
import com.akiban.server.expression.std.IntervalCastExpression;
import static com.akiban.server.expression.std.Expressions.*;
import com.akiban.server.service.functions.FunctionsRegistry;
import com.akiban.server.error.AkibanInternalException;
import com.akiban.server.types.AkType;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types.extract.Extractors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Turn {@link ExpressionNode} into {@link Expression}. */
public class OldExpressionAssembler extends ExpressionAssembler<Expression>
{
    private static final Logger logger = LoggerFactory.getLogger(OldExpressionAssembler.class);

    private final FunctionsRegistry functionsRegistry;

    public OldExpressionAssembler(PlanContext planContext) {
        super(planContext);
        RulesContext rulesContext = planContext.getRulesContext();
        functionsRegistry = ((SchemaRulesContext)rulesContext).getFunctionsRegistry();
    }

    public FunctionsRegistry getFunctionRegistry()
    {
        return functionsRegistry;
    }

    @Override
    protected Expression assembleFunction(ExpressionNode functionNode,
                                       String functionName,
                                       List<ExpressionNode> argumentNodes,
                                       ColumnExpressionContext columnContext,
                                       SubqueryOperatorAssembler<Expression> subqueryAssembler) {
        List<Expression> arguments = 
            assembleExpressions(argumentNodes, columnContext, subqueryAssembler);
        int nargs = arguments.size();
        List<ExpressionType> types = new ArrayList<>(nargs + 1);
        for (int i = 0; i < nargs; i++) {
            types.add(TypesTranslation.toExpressionType(argumentNodes.get(i).getSQLtype()));
        }
        types.add(TypesTranslation.toExpressionType(functionNode.getSQLtype()));
        return functionsRegistry.composer(functionName).compose(arguments, types);
    }

    @Override
    protected Expression assembleCastExpression(CastExpression castExpression,
                                                ColumnExpressionContext columnContext,
                                                SubqueryOperatorAssembler<Expression> subqueryAssembler) {
        ExpressionNode operand = castExpression.getOperand();
        Expression expr = assembleExpression(operand, columnContext, subqueryAssembler);
        AkType toType = castExpression.getAkType();
        if (toType == null) return expr;
        if (!toType.equals(operand.getAkType()))
        {
            // Do type conversion.
            TypeId id = castExpression.getSQLtype().getTypeId(); 
            if (id.isIntervalTypeId())
                expr = new IntervalCastExpression(expr, id);
            else 
                expr = new com.akiban.server.expression.std.CastExpression(toType, expr);
        }
        
        switch (toType) {
        case VARCHAR:
            {
                DataTypeDescriptor fromSQL = operand.getSQLtype();
                DataTypeDescriptor toSQL = castExpression.getSQLtype();
                if ((toSQL != null) &&
                    (toSQL.getMaximumWidth() > 0) &&
                    ((fromSQL == null) ||
                     (toSQL.getMaximumWidth() < fromSQL.getMaximumWidth())))
                    // Cast to shorter VARCHAR.
                    expr = new com.akiban.server.expression.std.TruncateStringExpression(toSQL.getMaximumWidth(), expr);
            }
            break;
        case DECIMAL:
            {
                DataTypeDescriptor fromSQL = operand.getSQLtype();
                DataTypeDescriptor toSQL = castExpression.getSQLtype();
                if ((toSQL != null) && !toSQL.equals(fromSQL))
                    // Cast to DECIMAL scale.
                    expr = new com.akiban.server.expression.std.ScaleDecimalExpression(toSQL.getPrecision(), toSQL.getScale(), expr);
            }
            break;
        }
        return expr;
    }

    @Override
    public ConstantExpression evalNow(PlanContext planContext, ExpressionNode node) {
        if (node instanceof ConstantExpression)
            return (ConstantExpression)node;
        Expression expr = assembleExpression(node, null, null);
        if (!expr.isConstant())
            throw new AkibanInternalException("required constant expression: " + expr);
        ExpressionEvaluation eval = expr.evaluation();
        eval.of(planContext.getQueryContext());
        ValueSource valueSource = eval.eval();
        if (node instanceof ConditionExpression) {
            Boolean value = Extractors.getBooleanExtractor().getBoolean(valueSource, null);
            return new BooleanConstantExpression(value,
                                                 node.getSQLtype(), 
                                                 node.getSQLsource());
        }
        else {
            return new ConstantExpression(valueSource,
                                          node.getSQLtype(), 
                                          node.getSQLsource());
        }
    }

    @Override
    protected Expression tryLiteral(ExpressionNode node) {
        return null;
    }

    @Override
    protected Expression literal(ConstantExpression expression) {
        if (expression.getAkType() == null)
            return Expressions.literal(expression.getValue());
        else
            return Expressions.literal(expression.getValue(), expression.getAkType());
    }

    @Override
    protected Expression variable(ParameterExpression expression) {
        return Expressions.variable(expression.getAkType(), expression.getPosition());
    }

    @Override
    protected Expression compare(Expression left, ComparisonCondition comparison, Expression right) {
        return Expressions.compare(left, comparison.getOperation(), right);
    }

    @Override
    protected Expression collate(Expression left, Comparison comparison, Expression right, AkCollator collator) {
        return Expressions.collate(left, comparison, right, collator);
    }

    @Override
    protected AkCollator collator(ComparisonCondition cond, Expression left, Expression right) {
        return ExpressionTypes.operationCollation(TypesTranslation.toExpressionType(cond.getLeft().getSQLtype()),
                                                  TypesTranslation.toExpressionType(cond.getRight().getSQLtype()));
    }

    @Override
    protected Expression in(Expression lhs, List<Expression> rhs, InListCondition inList) {
        return new InExpression(lhs, rhs);
    }

    @Override
    protected Expression assembleFieldExpression(RowType rowType, int fieldIndex) {
        return field(rowType, fieldIndex);
    }

    @Override
    protected Expression assembleBoundFieldExpression(RowType rowType, int rowIndex, int fieldIndex) {
        return boundField(rowType, rowIndex, fieldIndex);
    }

    @Override
    protected Expression assembleRoutine(ExpressionNode routineNode, 
                                         Routine routine,
                                         List<ExpressionNode> operandNodes,
                                         ColumnExpressionContext columnContext,
                                         SubqueryOperatorAssembler<Expression> subqueryAssembler) {
        List<Expression> inputs = assembleExpressions(operandNodes, columnContext, subqueryAssembler);
        switch (routine.getCallingConvention()) {
        case JAVA:
            return new ServerJavaMethodExpression(routine, inputs);
        case SCRIPT_FUNCTION_JAVA:
        case SCRIPT_FUNCTION_JSON:
            return new ScriptFunctionJavaRoutineExpression(routine, inputs);
        case SCRIPT_BINDINGS:
        case SCRIPT_BINDINGS_JSON:
            return new ScriptBindingsRoutineExpression(routine, inputs);
        default:
            throw new AkibanInternalException("Unimplemented routine " + routine);
        }
    }

    @Override
    public Operator assembleAggregates(Operator inputOperator, RowType rowType, int nkeys,
                                       AggregateSource aggregateSource)
    {
        List<String> names = aggregateSource.getAggregateFunctions();
        List<Object> options = aggregateSource.getOptions();
        return API.aggregate_Partial(
                inputOperator,
                rowType,
                nkeys,
                functionsRegistry,
                names,
                options);
    }

    @Override
    protected Logger logger() {
        return logger;
    }
}
