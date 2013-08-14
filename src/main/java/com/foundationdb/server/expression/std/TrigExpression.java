/**
 * Copyright (C) 2009-2013 Akiban Technologies, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.foundationdb.server.expression.std;
import com.foundationdb.server.error.OverflowException;
import com.foundationdb.server.error.WrongExpressionArityException;
import com.foundationdb.server.expression.Expression;
import com.foundationdb.server.expression.ExpressionComposer;
import com.foundationdb.server.expression.ExpressionEvaluation;
import com.foundationdb.server.expression.ExpressionType;
import com.foundationdb.server.service.functions.Scalar;
import com.foundationdb.server.types.AkType;
import com.foundationdb.server.types.NullValueSource;
import com.foundationdb.server.types.ValueSource;
import com.foundationdb.server.types.extract.DoubleExtractor;
import com.foundationdb.server.types.extract.Extractors;
import com.foundationdb.sql.StandardException;
import com.foundationdb.server.expression.TypesList;
import java.util.List;

public class TrigExpression extends AbstractCompositeExpression
{
    @Override
    public String name()
    {
        return name.name();
    }
    
    public static enum TrigName
    {
        SIN, COS, TAN, COT, ASIN, ACOS, ACOT, ATAN, ATAN2, COSH, SINH, TANH, COTH
    }
    
    private final TrigName name;
    
    @Scalar ("sin")
    public static final ExpressionComposer SIN_COMPOSER = new InternalComposer (TrigName.SIN);
    
    @Scalar ("cos")
    public static final ExpressionComposer COS_COMPOSER = new InternalComposer(TrigName.COS);
    
    @Scalar ("tan")
    public static final ExpressionComposer TAN_COMPOSER = new InternalComposer(TrigName.TAN);
    
    @Scalar ("cot")
    public static final ExpressionComposer COT_COMPOSER = new InternalComposer(TrigName.COT);
    
    @Scalar ("asin")
    public static final ExpressionComposer ASIN_COMPOSER = new InternalComposer(TrigName.ASIN);
    
    @Scalar ("acos")
    public static final ExpressionComposer ACOS_COMPOSER = new InternalComposer(TrigName.ACOS);
    
    @Scalar ("atan")
    public static final ExpressionComposer ATAN_COMPOSER = new InternalComposer(TrigName.ATAN);
    
    @Scalar ("atan2")
    public static final ExpressionComposer ATAN2_COMPOSER = new InternalComposer(TrigName.ATAN2);
    
    @Scalar ("cosh")
    public static final ExpressionComposer COSH_COMPOSER = new InternalComposer(TrigName.COSH);
    
    @Scalar ("sinh")
    public static final ExpressionComposer SINH_COMPOSER = new InternalComposer(TrigName.SINH);
    
    @Scalar ("tanh")
    public static final ExpressionComposer  TANH_COMPOSER = new InternalComposer(TrigName.TANH);
    
    @Scalar ("coth")
    public static final ExpressionComposer COTH_COMPOSER = new InternalComposer(TrigName.COTH);
    
    @Scalar ("acot")
    public static final ExpressionComposer ACOT_COMPOSER = new InternalComposer(TrigName.ACOT);
    
    private static class InternalComposer implements ExpressionComposer
    {
        private final TrigName name;
        
        public InternalComposer (TrigName name)
        {
            this.name = name;
        }

        @Override
        public ExpressionType composeType(TypesList argumentTypes) throws StandardException
        {
            int size = argumentTypes.size();

            switch(size)
            {
                case 2:     if (name != TrigName.ATAN && name != TrigName.ATAN2)
                                throw new WrongExpressionArityException(1, size); // fall thru
                case 1:     break;
                default:    throw new WrongExpressionArityException(1, size);
            }
           
            for (int i = 0; i < size; ++i)
                argumentTypes.setType(i, AkType.DOUBLE);
            return ExpressionTypes.DOUBLE;
        }

        @Override
        public Expression compose(List<? extends Expression> arguments, List<ExpressionType> typesList)
        {
            return new TrigExpression(arguments, name);
        }

        @Override
        public NullTreating getNullTreating()
        {
            return NullTreating.RETURN_NULL;
        }
    }
    
    private static class InnerEvaluation extends AbstractCompositeExpressionEvaluation
    {
        private final TrigName name;   

        public InnerEvaluation (List<? extends ExpressionEvaluation> children, TrigName name)
        {
             super(children);  
             this.name = name;
        }

        @Override
        public ValueSource eval() 
        {
            // get first operand
            ValueSource firstOperand = this.children().get(0).eval();
            if (firstOperand.isNull())
                return NullValueSource.only();
            
            DoubleExtractor dExtractor = Extractors.getDoubleExtractor();
            double dvar1 = dExtractor.getDouble(firstOperand);
            
            // get second operand, if any, for ATAN2/ATAN
            double dvar2 = 1;
            if (children().size() == 2)
            {
                ValueSource secOperand = this.children().get(1).eval();
                if (secOperand.isNull())
                    return NullValueSource.only();
                
                dvar2 = dExtractor.getDouble(secOperand);
            }
           
            double result = 0;
            double temp;
            switch (name)
            {
                case SIN:   result = Math.sin(dvar1); break;              
                case COS:   result = Math.cos(dvar1); break; 
                case TAN:   if ( Math.cos(dvar1) == 0)
                                throw new OverflowException ();
                            else result = Math.tan(dvar1);
                            break;
                case COT:   if ((temp = Math.sin(dvar1)) == 0)
                                throw new OverflowException ();
                            else result = Math.cos(dvar1) / temp;
                            break;
                case ACOT:  if ( dvar1 == 0) result = Math.PI /2;
                            else result = Math.atan(1 / dvar1);
                            break;
                case ASIN:  result = Math.asin(dvar1); break;
                case ACOS:  result = Math.acos(dvar1); break;                    
                case ATAN:  
                case ATAN2: result = Math.atan2(dvar1, dvar2); break;                    
                case COSH:  result = Math.cosh(dvar1); break;
                case SINH:  result = Math.sinh(dvar1); break;
                case TANH:  result = Math.tanh(dvar1); break;
                case COTH:  if (dvar1 == 0)
                                throw new OverflowException ();
                            else result = Math.cosh(dvar1) / Math.sinh(dvar1);
                            break;
                default: throw new UnsupportedOperationException("Unknown Operation: " + name.name());
            }

            valueHolder().putDouble(result);
            return valueHolder();
        }   
    }
     
    /**
     * Creates a trigonometry expression (SIN, COS, ...., ATAN, ATAN2, or COSH)
     * TrigExpression is null if its argument is null
     * @param children
     * @param name 
     */
    public TrigExpression (List <? extends Expression> children, TrigName name)
    { 
        super (AkType.DOUBLE, children);
        
        int size = children.size();
        switch(size)
        {
            case 2:     if (name != TrigName.ATAN && name != TrigName.ATAN2)
                            throw new WrongExpressionArityException(1, size); // fall thru
            case 1:     break;
            default:    throw new WrongExpressionArityException(1, size);
        }
        this.name = name;
    }
    
    @Override
    protected void describe(StringBuilder sb) 
    {
        sb.append(name.name()).append("_EXPRESSION");
    }
   
    @Override
    public ExpressionEvaluation evaluation() 
    {
       return new InnerEvaluation (this.childrenEvaluations(), name);
    }

    @Override
    public boolean nullIsContaminating()
    {
        return true;
    }
}
