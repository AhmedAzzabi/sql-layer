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

import com.foundationdb.server.error.WrongExpressionArityException;
import com.foundationdb.server.expression.Expression;
import com.foundationdb.server.expression.ExpressionComposer;
import com.foundationdb.server.expression.ExpressionEvaluation;
import com.foundationdb.server.expression.ExpressionType;
import com.foundationdb.server.service.functions.Scalar;
import com.foundationdb.server.types.AkType;
import com.foundationdb.server.types.NullValueSource;
import com.foundationdb.server.types.ValueSource;
import com.foundationdb.server.types.util.ValueHolder;
import com.foundationdb.sql.StandardException;
import com.foundationdb.server.expression.TypesList;
import java.util.Arrays;
import java.util.List;

public class LocateExpression extends AbstractCompositeExpression
{
    @Scalar ("position")
    public static final ExpressionComposer POSITION_COMPOSER = new BinaryComposer ()
    {

        @Override
        protected Expression compose(Expression first, Expression second, ExpressionType firstType, ExpressionType secondType, ExpressionType resultType) 
        {
            return new LocateExpression(Arrays.asList(first, second));
        }

        @Override
        public ExpressionType composeType(TypesList argumentTypes) throws StandardException
        {
            if (argumentTypes.size() != 2) throw new WrongExpressionArityException(2, argumentTypes.size());
            argumentTypes.setType(0, AkType.VARCHAR);
            argumentTypes.setType(1, AkType.VARCHAR);

            return ExpressionTypes.LONG;
        }        
    };
    
    @Scalar ("locate")
    public static final ExpressionComposer LOCATE_COMPOSER = new ExpressionComposer ()
    {
        @Override
        public ExpressionType composeType(TypesList argumentTypes) throws StandardException
        {
            int s = argumentTypes.size();
            if (s!= 2 && s != 3) throw new WrongExpressionArityException(2, s);
            argumentTypes.setType(0, AkType.VARCHAR);
            argumentTypes.setType(1, AkType.VARCHAR);
            if (s == 3) argumentTypes.setType(2, AkType.LONG);

            return ExpressionTypes.LONG;
        }

        @Override
        public Expression compose(List<? extends Expression> arguments, List<ExpressionType> typesList)
        {
            return new LocateExpression(arguments);
        }

        @Override
        public NullTreating getNullTreating()
        {
            return NullTreating.RETURN_NULL;
        }
        
    };

    @Override
    public String name()
    {
        return "LOCATE";
    }
    
    private static final class InnerEvaluation extends AbstractCompositeExpressionEvaluation
    {
        public InnerEvaluation (List<? extends ExpressionEvaluation> childrenEval)
        {
            super(childrenEval);
        }

        @Override
        public ValueSource eval() 
        {
            // str operand
            ValueSource substrOp = children().get(0).eval();
            if (substrOp.isNull()) return NullValueSource.only();
            
            // substr operand
            ValueSource strOp = children().get(1).eval();
            if (substrOp.isNull()) return NullValueSource.only();
            
            String str = strOp.getString();
            String substr = substrOp.getString();
           
            // optional pos operand
            long pos = 0;
            if (children().size() == 3)
            {
                ValueSource posOp = children().get(2).eval();
                if (posOp.isNull()) return NullValueSource.only();
                pos = posOp.getLong() -1;
                if (pos < 0 || pos > str.length()) return new ValueHolder(AkType.LONG, 0L);
            }

            valueHolder().putLong(1 + (long)str.indexOf(substr, (int)pos));
            return valueHolder();
        }
        
    }
    
    public LocateExpression (List<? extends Expression> children)
    {
        super(AkType.LONG, checkArgs(children));
    }

    private static List<? extends Expression> checkArgs (List <? extends Expression> children)
    {
        if (children.size() != 2 && children.size() != 3) throw new WrongExpressionArityException(2, children.size());
        return children;
    }
    
    @Override
    public boolean nullIsContaminating() 
    {
        return true;
    }
    
    @Override
    protected void describe(StringBuilder sb) 
    {
        sb.append(name());
    }

    @Override
    public ExpressionEvaluation evaluation() 
    {
        return new InnerEvaluation(childrenEvaluations());
    }
}
