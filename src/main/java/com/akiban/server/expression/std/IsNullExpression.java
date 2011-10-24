/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */


package com.akiban.server.expression.std;

import com.akiban.server.expression.Expression;
import com.akiban.server.expression.ExpressionComposer;
import com.akiban.server.expression.ExpressionEvaluation;
import com.akiban.server.service.functions.Scalar;
import com.akiban.server.types.AkType;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types.util.ValueHolder;


public class IsNullExpression extends AbstractUnaryExpression
{
    @Scalar("isnull")
    public static final ExpressionComposer COMPOSER = new  UnaryComposer ()
    {
        @Override
        protected Expression compose(Expression argument) 
        {
            return new IsNullExpression(argument);
        }
    };
        
    private static final class InnerEvaluation extends AbstractUnaryExpressionEvaluation
    {
        public InnerEvaluation (ExpressionEvaluation ev)
        {
            super(ev);
        }

        @Override
        public ValueSource eval() 
        {
           return new ValueHolder (AkType.BOOL, this.operand() == null || this.operand().isNull());
        }
         
     }
    
    public IsNullExpression (Expression e)
    {
        super(AkType.BOOL, e == null ? new LiteralExpression(AkType.NULL, null) : e);
    }

    @Override
    protected String name() 
    {
        return "IsNull";
    }

    @Override
    public ExpressionEvaluation evaluation() 
    {
        return new InnerEvaluation(this.operandEvaluation());
    }
}
