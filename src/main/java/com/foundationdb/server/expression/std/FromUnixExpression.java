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
import com.akiban.server.expression.Expression;
import com.akiban.server.expression.ExpressionComposer;
import com.akiban.server.expression.ExpressionEvaluation;
import com.akiban.server.expression.ExpressionType;
import com.akiban.server.expression.TypesList;
import com.akiban.server.service.functions.Scalar;
import com.akiban.server.types.AkType;
import com.akiban.server.types.NullValueSource;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types.util.ValueHolder;
import com.akiban.sql.StandardException;
import java.util.List;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.MutableDateTime;

public class FromUnixExpression extends AbstractCompositeExpression
{
    @Override
    protected void describe(StringBuilder sb)
    {
        sb.append("FROM_UNIXTIME()");
    }
    
    @Scalar("from_unixtime")
    public static final ExpressionComposer COMPOSER = new ExpressionComposer ()
    {
        @Override
        public ExpressionType composeType(TypesList argumentTypes) throws StandardException
        {
            int s = argumentTypes.size();
            switch (s)
            {
                case 2: argumentTypes.setType(1, AkType.VARCHAR);
                        argumentTypes.setType(0, AkType.LONG);
                        return ExpressionTypes.varchar(argumentTypes.get(0).getPrecision() * 5);
                case 1: argumentTypes.setType(0, AkType.LONG);
                        return ExpressionTypes.DATETIME;
                default: throw new WrongExpressionArityException(1, s);
            }
        }

        @Override
        public Expression compose(List<? extends Expression> arguments, List<ExpressionType> typesList)
        {
            return new FromUnixExpression(arguments);
        }

        @Override
        public NullTreating getNullTreating()
        {
            return NullTreating.RETURN_NULL;
        }
    };

    @Override
    public boolean nullIsContaminating()
    {
        return true;
    }

    @Override
    public String name()
    {
        return "FROM_UNIXTIME";
    }

    private static class InnerEvaluation extends AbstractCompositeExpressionEvaluation
    {
        public InnerEvaluation (List< ? extends ExpressionEvaluation> ev)
        {
            super(ev);
        }

        @Override
        public ValueSource eval()
        {
            ValueSource dateS = children().get(0).eval();
            if (dateS.isNull()) return NullValueSource.only();
            long unix = dateS.getLong() * 1000;

            switch(children().size())
            {
                case 1:     return new ValueHolder(AkType.DATETIME, new DateTime(unix, DateTimeZone.getDefault()));

                default:    ValueSource str = children().get(1).eval();
                            if (str.isNull())
                                return NullValueSource.only();
                            else
                                return new ValueHolder(AkType.VARCHAR, DateTimeField.getFormatted(
                                        new MutableDateTime(unix, DateTimeZone.getDefault()),
                                        str.getString()));
            }

        }
    }

    /**
     * Takes an UNIX_TIME, which is the number of SECONDS from epoch and optionally
     * a format str
     *
     * @param ex
     * @return  DATETIME in <b>current</b> timezone if no format string is passed
     *          VARCHAR representing the datetime formated accordingly if format string is passed
     */
    public FromUnixExpression (List<? extends Expression> ex)
    {
        super(checkArg(ex), ex);
    }

    protected static AkType checkArg (List<? extends Expression> ex)
    {
        if (ex.size() != 2 && ex.size() != 1) throw new WrongExpressionArityException(1, ex.size());
        return ex.size() == 2 ? AkType.VARCHAR : AkType.DATETIME;
    }

    @Override
    public ExpressionEvaluation evaluation()
    {
        return new InnerEvaluation(childrenEvaluations());
    }
}
