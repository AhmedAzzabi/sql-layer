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

package com.akiban.server.expression.std;

import com.akiban.server.error.WrongExpressionArityException;
import com.akiban.server.expression.*;
import com.akiban.server.service.functions.Scalar;
import com.akiban.server.types.AkType;
import com.akiban.server.types.NullValueSource;
import com.akiban.server.types.ValueSource;
import com.akiban.sql.StandardException;

public class SqrtExpression extends AbstractUnaryExpression {
    
    @Scalar("sqrt")
    public static final ExpressionComposer COMPOSER = new InternalComposer();
    
    private static class InternalComposer extends UnaryComposer
    {

        @Override
        protected Expression compose(Expression argument, ExpressionType argType, ExpressionType resultType)
        {
            return new SqrtExpression(argument);
        }

        @Override
        public ExpressionType composeType(TypesList argumentTypes) throws StandardException
        {
            if (argumentTypes.size() != 1)
                throw new WrongExpressionArityException(1, argumentTypes.size());

            argumentTypes.setType(0, AkType.DOUBLE);
            return ExpressionTypes.DOUBLE;
        }
  
    }
    
    private static class InnerEvaluation extends AbstractUnaryExpressionEvaluation
    {

        @Override
        public ValueSource eval()
        {
            if (operand().isNull())
                return NullValueSource.only();
            
            if (operand().getDouble() < 0)
            {
                valueHolder().putDouble(Double.NaN);
                return valueHolder();
            }
            
            double sqrtResult = Math.sqrt(operand().getDouble());
            valueHolder().putDouble(sqrtResult);

            return valueHolder();
        }
        
        public InnerEvaluation(ExpressionEvaluation eval)
        {
            super(eval);
        }
        
    }
    
    @Override
    public String name()
    {
        return "sqrt";
    }

    @Override
    public ExpressionEvaluation evaluation()
    {
        return new InnerEvaluation(this.operandEvaluation());
    }
    
    protected SqrtExpression(Expression operand)
    {
        super(AkType.DOUBLE, operand);
    }

}
