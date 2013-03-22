
package com.akiban.server.expression.std;

import com.akiban.qp.operator.QueryContext;
import com.akiban.server.error.InconvertibleTypesException;
import com.akiban.server.error.InvalidCharToNumException;
import com.akiban.server.error.WrongExpressionArityException;
import com.akiban.server.expression.Expression;
import com.akiban.server.expression.ExpressionComposer;
import com.akiban.server.expression.ExpressionEvaluation;
import com.akiban.server.expression.ExpressionType;
import com.akiban.server.service.functions.Scalar;
import com.akiban.server.types.AkType;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types.extract.Extractors;
import com.akiban.server.types.extract.LongExtractor;
import com.akiban.server.types.extract.ObjectExtractor;
import com.akiban.server.expression.TypesList;
import com.akiban.sql.StandardException;
import com.google.common.primitives.Longs;
import com.google.common.primitives.UnsignedLongs;

import java.math.BigInteger;
import java.util.List;

public class BinaryBitExpression extends AbstractBinaryExpression
{
    @Override
    public String name()
    {
        return op.name();
    }
    
    public static enum BitOperator
    {
        BITWISE_AND
        {
            @Override
            protected BigInteger exc (ValueSource left, ValueSource right)
            {
                return bIntExtractor.getObject(left).and(bIntExtractor.getObject(right));
            }
        },
        BITWISE_OR
        {
            @Override
            public BigInteger exc (ValueSource left, ValueSource right)
            {
                return bIntExtractor.getObject(left).or(bIntExtractor.getObject(right));
            }
        },
        BITWISE_XOR
        {
            @Override
            protected BigInteger exc (ValueSource left, ValueSource right)
            {
                return bIntExtractor.getObject(left).xor(bIntExtractor.getObject(right));
            }
        },
        LEFT_SHIFT
        {
            @Override
            protected BigInteger exc (ValueSource left, ValueSource right)
            {
                BigInteger lhs = bIntExtractor.getObject(left);
                long shiftBy = lExtractor.getLong(right);
                if (shiftBy < 0) {
                    return BigInteger.ZERO;
                }
                return lhs.shiftLeft((int) shiftBy);
            }
        },
        RIGHT_SHIFT
        {
            @Override
            protected BigInteger exc (ValueSource left, ValueSource right)
            {
                BigInteger lhs = bIntExtractor.getObject(left);
                long shiftBy = lExtractor.getLong(right);
                if (shiftBy < 0)
                    return BigInteger.ZERO;
                return lhs.shiftRight((int) shiftBy);
            }
        };

        protected abstract BigInteger exc (ValueSource left,ValueSource right);
        private static ObjectExtractor<BigInteger> bIntExtractor = Extractors.getUBigIntExtractor();
        private static LongExtractor lExtractor = Extractors.getLongExtractor(AkType.LONG);
    }
    
    @Scalar("bitand")
    public static final ExpressionComposer B_AND_COMPOSER = new InternalComposer(BitOperator.BITWISE_AND);
    
    @Scalar("bitor")
    public static final ExpressionComposer B_OR_COMPOSER = new InternalComposer(BitOperator.BITWISE_OR);
    
    @Scalar("bitxor")
    public static final ExpressionComposer B_XOR_COMPOSER = new InternalComposer(BitOperator.BITWISE_XOR);
    
    @Scalar("leftshift")
    public static final ExpressionComposer LEFT_SHIFT_COMPOSER = new InternalComposer(BitOperator.LEFT_SHIFT);
    
    @Scalar("rightshift")
    public static final ExpressionComposer RIGHT_SHIFT_COMPOSER = new InternalComposer(BitOperator.RIGHT_SHIFT);
        
    private final BitOperator op;
       
    protected static class InternalComposer extends BinaryComposer
    {
        protected final BitOperator op;
        
        public InternalComposer (BitOperator op)
        {
            this.op = op;
        }      

        @Override
        protected Expression compose(Expression first, Expression second, ExpressionType firstType, ExpressionType secondType, ExpressionType resultType) 
        {
            return new BinaryBitExpression(first, op,second);
        }

        @Override
        public ExpressionType composeType(TypesList argumentTypes) throws StandardException
        {
            if (argumentTypes.size() != 2)
                throw new WrongExpressionArityException(2, argumentTypes.size());
            argumentTypes.setType(0, AkType.U_BIGINT);
            argumentTypes.setType(1, op.ordinal() >= BitOperator.LEFT_SHIFT.ordinal() ?
                                        AkType.LONG : AkType.U_BIGINT);
            return ExpressionTypes.U_BIGINT;
        }
    }

    protected static class InnerEvaluation extends AbstractTwoArgExpressionEvaluation
    {
        private final BitOperator op;                
        private static final BigInteger n64 = new BigInteger("FFFFFFFFFFFFFFFF", 16);        
        
        public InnerEvaluation (List<? extends ExpressionEvaluation> children, BitOperator op)
        {
            super(children);
            this.op = op;
        }
        
        @Override
        public ValueSource eval() 
        {
            BigInteger rst = BigInteger.ZERO;
            try
            {
                rst = op.exc(left(), right());
            }
            catch (InconvertibleTypesException ex) // acceptable error where the result will simply be 0
            {
                // if invalid types are supplied, 0 is assumed to be input
               QueryContext context = queryContext();
               if (context != null)
                   context.warnClient(ex);
            }   
            catch (NumberFormatException exc ) // acceptable error where the result will simply be 0
            {
                QueryContext context = queryContext();
                if (context != null)
                   context.warnClient(new InvalidCharToNumException(exc.getMessage()));
            }
            valueHolder().putUBigInt(rst.and(n64));
            return valueHolder();
        }
    }
    
    public BinaryBitExpression (Expression lhs, BitOperator op, Expression rhs)
    {
        super(lhs.valueType() == AkType.NULL || rhs.valueType() == AkType.NULL ? AkType.NULL : AkType.U_BIGINT
                , lhs, rhs);
        this.op = op;        
    } 
    
    @Override
    protected void describe(StringBuilder sb) 
    {
        sb.append(op);
    }

    @Override
    public ExpressionEvaluation evaluation() 
    {  
        if (valueType() == AkType.NULL ) return LiteralExpression.forNull().evaluation();
        return new InnerEvaluation(childrenEvaluations(), op);
    }    
        
    @Override
    public boolean nullIsContaminating ()
    {
        return true;
    }
}
