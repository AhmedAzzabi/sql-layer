
package com.akiban.server.expression.std;

import com.akiban.server.error.WrongExpressionArityException;
import com.akiban.server.expression.Expression;
import com.akiban.server.expression.ExpressionComposer;
import com.akiban.server.expression.ExpressionEvaluation;
import com.akiban.server.expression.ExpressionType;
import com.akiban.server.expression.TypesList;
import com.akiban.server.service.functions.Scalar;
import com.akiban.server.types.AkType;
import com.akiban.server.types.NullValueSource;
import com.akiban.server.types.ValueSource;
import com.akiban.sql.StandardException;
import java.nio.charset.Charset;

public class HexExpression extends AbstractUnaryExpression
{
    @Scalar("hex")
    public static final ExpressionComposer COMPOSER = new UnaryComposer()
    {
        @Override
        protected Expression compose(Expression argument, ExpressionType argType, ExpressionType resultType)
        {
            return new HexExpression(argument);
        }

        @Override
        public ExpressionType composeType(TypesList argumentTypes) throws StandardException
        {
            if (argumentTypes.size() != 1)
                throw new WrongExpressionArityException(1, argumentTypes.size());
            if (ArithExpression.isNumeric(argumentTypes.get(0).getType()))
                argumentTypes.setType(0, AkType.LONG);
            else
                argumentTypes.setType(0, AkType.VARCHAR);
            return ExpressionTypes.varchar(argumentTypes.get(0).getPrecision() * 2);
        }
        
    };
    
    private static class InnerEvaluation extends AbstractUnaryExpressionEvaluation
    {
        private  Charset charset = Charset.defaultCharset(); // may change when actual charset
                                                          // becomes availabe to expressions
        
        public InnerEvaluation(ExpressionEvaluation eval)
        {
            super(eval);
        }

        @Override
        public ValueSource eval()
        {
            ValueSource source = operand();
            if (source.isNull())
                return NullValueSource.only();
            
            if (source.getConversionType() == AkType.LONG)
                valueHolder().putString(Long.toHexString(source.getLong()).toUpperCase());
            else
            {
                StringBuilder builder = new StringBuilder();
                for (byte ch : source.getString().getBytes(charset))
                    builder.append(String.format("%02X", ch));
                valueHolder().putString(builder.toString());
            }
            
            return valueHolder();
        }
        
    }

    @Override
    public String name()
    {
        return "HEX";
    }

    @Override
    public ExpressionEvaluation evaluation()
    {
        return new InnerEvaluation(operandEvaluation());
    }
    
    protected HexExpression (Expression arg)
    {
        super(AkType.VARCHAR, arg);
    }
}
