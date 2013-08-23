/**
 * Copyright (C) 2009-2013 FoundationDB, LLC
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
import com.foundationdb.server.types.ValueSource;
import com.foundationdb.server.types.util.BoolValueSource;
import com.foundationdb.sql.StandardException;
import com.foundationdb.server.expression.TypesList;

public class IsExpression extends AbstractUnaryExpression
{    
   @Scalar ("istrue")
   public static final ExpressionComposer IS_TRUE = new InnerComposer(TriVal.TRUE);

   @Scalar ("isfalse")
   public static final ExpressionComposer IS_FALSE = new InnerComposer(TriVal.FALSE);

   @Scalar ("isunknown")
   public static final ExpressionComposer IS_UNKNOWN= new InnerComposer(TriVal.UNKNOWN);


   protected static enum TriVal
   {
        TRUE, FALSE, UNKNOWN
   }

   private static class InnerComposer  extends UnaryComposer
   {
       protected final TriVal triVal;
       
       protected InnerComposer (TriVal triVal)
       {
           this.triVal = triVal;
       }

        @Override
        protected Expression compose(Expression argument, ExpressionType argType, ExpressionType resultType)
        {
            return new IsExpression(argument, triVal);
        }

       @Override
       public String toString()
       {
           return "IS " + triVal;
       }

        @Override
        public ExpressionType composeType(TypesList argumentTypes) throws StandardException
        {
            if (argumentTypes.size() != 1)
                throw new WrongExpressionArityException(1, argumentTypes.size());
            argumentTypes.setType(0, AkType.BOOL);
            return ExpressionTypes.BOOL;
        }
    }

    private static class InnerEvaluation extends AbstractUnaryExpressionEvaluation
    {
        private final TriVal triVal;

        public InnerEvaluation (ExpressionEvaluation operandEval, TriVal triVal)
        {
            super(operandEval);
            this.triVal = triVal;
        }
        
        @Override
        public ValueSource eval()
        {
            ValueSource source = operand();


            if (source.isNull())            
                return BoolValueSource.of(triVal == TriVal.UNKNOWN);            
            else
                switch (triVal)
                {
                    case TRUE:  return BoolValueSource.of(source.getBool());
                    case FALSE: return BoolValueSource.of(!source.getBool()); 
                    default:    return BoolValueSource.of(false); 
                }
        }
    }

    private final TriVal triVal;
    
    protected IsExpression (Expression arg, TriVal triVal)
    {
        super(AkType.BOOL, arg);
        this.triVal = triVal;
    }

    @Override
    public String name()
    {
        return "IS " + triVal;
    }

    @Override
    public ExpressionEvaluation evaluation()
    {
        return new InnerEvaluation(operandEvaluation(), triVal);
    }
}
