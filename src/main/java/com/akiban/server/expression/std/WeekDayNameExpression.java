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

import com.akiban.qp.operator.QueryContext;
import com.akiban.server.error.InvalidParameterValueException;
import com.akiban.server.error.WrongExpressionArityException;
import com.akiban.server.expression.Expression;
import com.akiban.server.expression.ExpressionComposer;
import com.akiban.server.expression.ExpressionEvaluation;
import com.akiban.server.expression.ExpressionType;
import com.akiban.server.service.functions.Scalar;
import com.akiban.server.types.AkType;
import com.akiban.server.types.NullValueSource;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types.conversion.util.ConversionUtil;
import com.akiban.sql.StandardException;
import com.akiban.server.expression.TypesList;
import org.joda.time.MutableDateTime;
import org.joda.time.IllegalFieldValueException;

public class WeekDayNameExpression extends AbstractUnaryExpression
{
    protected static enum Field
    {
        DAYNAME, DAYOFWEEK, WEEKDAY
    }

    /**
     * Returns the day name
     * Eg., DAYNAME("2011-12-05") --> Monday
     */
    @Scalar("dayname")
    public static final ExpressionComposer DAYNAME_COMPOSER = new InternalComposer(Field.DAYNAME);

    /**
     * Returns the weekday index for date (1 = Sunday, 2 = Monday, …, 7 = Saturday).
     * These index values correspond to the ODBC standard.
     */
    @Scalar("dayofweek")
    public static final ExpressionComposer DAYOFWEEK_COMPOSER = new InternalComposer(Field.DAYOFWEEK);

    /**
     * Returns the weekday index for date (0 = Monday, 1 = Tuesday, … 6 = Sunday).
     */
    @Scalar("weekday")
    public static final ExpressionComposer WEEKDAY_COMPOSER = new InternalComposer(Field.WEEKDAY);
    
    private static class InternalComposer extends UnaryComposer
    {
        private final Field field;

        public InternalComposer(Field field)
        {
            this.field = field;
        }

        @Override
        protected Expression compose(Expression argument, ExpressionType argType, ExpressionType resultType)
        {
            return new WeekDayNameExpression(argument, field);
        }

        @Override
        public String toString()
        {
            return field.name();
        }

        @Override
        public ExpressionType composeType(TypesList argumentTypes) throws StandardException
        {
            if (argumentTypes.size() != 1)
                throw new WrongExpressionArityException(1, argumentTypes.size());

            ExpressionType givenType = argumentTypes.get(0);
            switch (givenType.getType())
            {
                case DATE:
                case DATETIME:
                case TIMESTAMP: break;
                case VARCHAR:   argumentTypes.setType(0, givenType.getPrecision() > 10 ?
                                                            AkType.DATETIME: AkType.DATE);
                                break;
                default:        argumentTypes.setType(0, AkType.DATE);
            }

            switch(field)
            {
                case DAYNAME:   return ExpressionTypes.varchar(9); // max length of a day-of-week name is 9
                default:        return ExpressionTypes.INT;
            }
        }
    }
    private static class InnerEvaluation extends AbstractUnaryExpressionEvaluation
    {
        private final Field field;        
        
        public InnerEvaluation(ExpressionEvaluation ev, Field field)
        {
            super(ev);
            this.field = field;
        }

        @Override
        public ValueSource eval()
        {
            ValueSource s = operand();
            if (s.isNull()) return NullValueSource.only();

            MutableDateTime datetime;
            try
            {
                datetime = ConversionUtil.getDateTimeConverter().get(s);
            }
            catch (IllegalFieldValueException ex)
            {
                QueryContext context = queryContext();
                if (context != null)
                    context.warnClient(new InvalidParameterValueException(ex.getLocalizedMessage()));
                return NullValueSource.only();
            }

            switch(field)
            {
                case DAYNAME:           valueHolder().putRaw(AkType.VARCHAR, datetime.dayOfWeek().getAsText());
                                        return valueHolder();
                
                                        // joda:            mon = 1, ..., sat = 6, sun = 7
                                        // mysql DAYOFWEEK: mon = 2, ..., sat = 7, sun = 1
                case DAYOFWEEK:         valueHolder().putRaw(AkType.INT, datetime.getDayOfWeek() % 7 + 1);
                                        return valueHolder();

                                        // joda:            mon = 1,..., sat = 6, sun = 7
                                        // mysql WEEKDAY:   mon = 0,..., sat = 5, sun = 6
                default: /*WEEKDAY*/    valueHolder().putRaw(AkType.INT, datetime.getDayOfWeek() -1);
                                        return valueHolder();
            }
        }
    }

    public WeekDayNameExpression (Expression arg, Field field)
    {
        super(field == Field.DAYNAME? AkType.VARCHAR : AkType.INT, arg);
        this.field = field;
    }

    @Override
    public String name()
    {
        return field.name();
    }

    @Override
    public ExpressionEvaluation evaluation()
    {
        return new InnerEvaluation(operandEvaluation(), field);
    }
    
    private final Field field;
}
