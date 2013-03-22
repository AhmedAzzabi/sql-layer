
package com.akiban.server.expression.std;

import com.akiban.server.error.WrongExpressionArityException;
import com.akiban.server.expression.Expression;
import com.akiban.server.expression.ExpressionComposer;
import com.akiban.server.expression.ExpressionEvaluation;
import com.akiban.server.expression.ExpressionType;
import com.akiban.server.service.functions.Scalar;
import com.akiban.server.types.AkType;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types.extract.Extractors;
import com.akiban.sql.StandardException;
import com.akiban.server.expression.TypesList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

public class IfExpression extends AbstractCompositeExpression
{
    @Override
    protected void describe(StringBuilder sb)
    {
        sb.append("IF()");
    }

    @Scalar("if")
    public static final ExpressionComposer COMPOSER = new ExpressionComposer()
    {
        @Override
        public ExpressionType composeType(TypesList argumentTypes) throws StandardException
        {
            int size = argumentTypes.size();
            if (size != 3)
                throw new WrongExpressionArityException(3, size);
            else
            {
                argumentTypes.setType(0, AkType.BOOL);
                AkType topType = CoalesceExpression.getTopType(Arrays.asList(argumentTypes.get(1).getType(), 
                                                                             argumentTypes.get(2).getType()));
                argumentTypes.setType(1, topType);
                argumentTypes.setType(2, topType);

                return ExpressionTypes.newType(topType,
                        Math.max(argumentTypes.get(1).getPrecision(), argumentTypes.get(2).getPrecision()),
                        Math.max(argumentTypes.get(1).getScale(), argumentTypes.get(2).getScale()));
            }

        }

        @Override
        public Expression compose(List<? extends Expression> arguments, List<ExpressionType> typesList)
        {
            return new IfExpression(arguments);
        }

        @Override
        public NullTreating getNullTreating()
        {
            return NullTreating.IGNORE;
        }
    };
    protected static final EnumSet<AkType> STRING = EnumSet.of(AkType.VARCHAR, AkType.TEXT);

    protected static AkType checkArgs(List<? extends Expression> children)
    {
        if (children.size() != 3)
            throw new WrongExpressionArityException(3, children.size());
        else
            return CoalesceExpression.getTopType(Arrays.asList(children.get(1).valueType(),
                                                               children.get(2).valueType()));
    }

    @Override
    public String name()
    {
        return "IF";
    }

    private static class InnerEvaluation extends AbstractCompositeExpressionEvaluation
    {
        public InnerEvaluation(List<? extends ExpressionEvaluation> eva)
        {
            super(eva);
        }

        @Override
        public ValueSource eval()
        {
            return children().get(Extractors.getBooleanExtractor().getBoolean(children().get(0).eval(), false).booleanValue() ? 1 : 2).eval();
        }
    }
    
    public IfExpression(List<? extends Expression> children)
    {
        super(checkArgs(children), children);
    }

    @Override
    public boolean nullIsContaminating()
    {
        return false;
    }

    @Override
    public ExpressionEvaluation evaluation()
    {
        return new InnerEvaluation(childrenEvaluations());
    }
}
