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

import com.foundationdb.server.error.InconvertibleTypesException;
import com.foundationdb.junit.OnlyIfNot;
import com.foundationdb.junit.OnlyIf;
import com.foundationdb.server.types.extract.Extractors;
import com.foundationdb.junit.ParameterizationBuilder;
import com.foundationdb.junit.Parameterization;
import java.util.Collection;
import org.junit.runner.RunWith;
import com.foundationdb.junit.NamedParameterizedRunner;
import java.math.BigDecimal;
import com.foundationdb.server.expression.Expression;
import com.foundationdb.server.types.util.ValueHolder;
import com.foundationdb.server.types.AkType;
import java.math.BigInteger;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

@RunWith(NamedParameterizedRunner.class)
public class IfExpressionTest 
{
    private AkType condType;
    private AkType trueExType;
    private AkType falseExType;
    private boolean evaluateRes;
    private ValueHolder expected;

    public IfExpressionTest (AkType condType, AkType trueExType, AkType falseExType, boolean evaluateRes, ValueHolder expected)
    {
        this.condType = condType;
        this.trueExType = trueExType;
        this.falseExType = falseExType;
        this.evaluateRes = evaluateRes;
        this.expected = expected;
    }

    @NamedParameterizedRunner.TestParameters
    public static Collection<Parameterization> params()
    {
        ParameterizationBuilder pb = new ParameterizationBuilder();

        // condition is all types in Aktype
        for (AkType t : AkType.values())
        {
           // skip NULL, UNSUPPORTED, VARBINARY and RESULT_SET
            if (t == AkType.NULL  || t == AkType.UNSUPPORTED || t == AkType.VARBINARY || t == AkType.RESULT_SET) continue;

            param(pb, t, AkType.LONG, AkType.LONG, true, new ValueHolder(AkType.LONG, 1L));
            param(pb, t, AkType.LONG, AkType.VARCHAR, true, new ValueHolder(AkType.VARCHAR, "1"));
            param(pb, t, AkType.DOUBLE, AkType.LONG, false, new ValueHolder(AkType.DOUBLE, 0.0));
            param(pb, t, AkType.VARCHAR, AkType.DOUBLE, false, new ValueHolder(AkType.VARCHAR, "0.0"));
            param(pb, t, AkType.VARCHAR, AkType.VARCHAR, true, new ValueHolder(AkType.VARCHAR, "true"));
            param(pb, t, AkType.DATE, AkType.DATE, true, new ValueHolder(AkType.DATE, 1L));
            param(pb, t, AkType.VARCHAR, AkType.DATE, false, new ValueHolder(AkType.VARCHAR, Extractors.getLongExtractor(AkType.DATE).asString(0L)));
            param(pb, t, AkType.BOOL, AkType.BOOL, false, new ValueHolder(AkType.BOOL, false));
            param(pb, t, AkType.DECIMAL, AkType.VARCHAR, true, new ValueHolder(AkType.VARCHAR, "1"));
            param(pb, t, AkType.DECIMAL, AkType.LONG, false, new ValueHolder(AkType.DECIMAL, BigDecimal.ZERO));

        }

        // condition is NULL or UNSUPPORTED => always false
        paramNullAndUnsupported(pb, AkType.LONG, AkType.LONG, false, new ValueHolder(AkType.LONG, 0L));
        paramNullAndUnsupported(pb, AkType.LONG, AkType.VARCHAR, false, new ValueHolder(AkType.VARCHAR, "0"));
        paramNullAndUnsupported(pb, AkType.DOUBLE, AkType.LONG, false, new ValueHolder(AkType.DOUBLE, 0.0));
        paramNullAndUnsupported(pb, AkType.VARCHAR, AkType.DOUBLE, false, new ValueHolder(AkType.VARCHAR, "0.0"));
        paramNullAndUnsupported(pb, AkType.VARCHAR, AkType.VARCHAR, false, new ValueHolder(AkType.VARCHAR, "0"));
        paramNullAndUnsupported(pb, AkType.DATE, AkType.DATE, false, new ValueHolder(AkType.DATE, 0L));
        paramNullAndUnsupported(pb, AkType.VARCHAR, AkType.DATE, false, new ValueHolder(AkType.VARCHAR, Extractors.getLongExtractor(AkType.DATE).asString(0L)));
        paramNullAndUnsupported(pb, AkType.DECIMAL, AkType.VARCHAR, false, new ValueHolder(AkType.VARCHAR, "0"));
        paramNullAndUnsupported(pb, AkType.DECIMAL, AkType.LONG, false, new ValueHolder(AkType.DECIMAL, BigDecimal.ZERO));
       
        //TODO: VARBINARY: can't deal with it yet
        
        return pb.asList();
    }

    private static void param(ParameterizationBuilder pb,AkType condType, AkType trueExType,
            AkType falseExType, boolean evaluateRes, ValueHolder expected )
    {
        pb.add("if(" + condType + ", " + trueExType + ", " + falseExType + ")===>" + evaluateRes,
                condType, trueExType, falseExType, evaluateRes, expected);
    }

    private static void paramNullAndUnsupported (ParameterizationBuilder pb, AkType trueExType,
            AkType falseExType, boolean evaluateRes, ValueHolder expected)
    {
        pb.add("if(" + AkType.NULL + ", " + trueExType + ", " + falseExType + ")===>" + evaluateRes,
                AkType.NULL, trueExType, falseExType, evaluateRes, expected);
        pb.add("if(" + AkType.UNSUPPORTED + ", " + trueExType + ", " + falseExType + ")===>" + evaluateRes,
                AkType.UNSUPPORTED, trueExType, falseExType, evaluateRes, expected);
    }

    private void test()
    {        
        Expression cond = getExp(condType, evaluateRes);
        Expression trExp = getExp(trueExType, true);
        Expression faExp = getExp(falseExType, false);        

        // cast second and third args as necessary
        AkType top = CoalesceExpression.getTopType(Arrays.asList(trueExType, falseExType));
        trExp = new CastExpression(top, trExp);
        faExp = new CastExpression(top, faExp);

        Expression ifExp = new IfExpression(Arrays.asList(cond, trExp, faExp));
        assertEquals (expected, new ValueHolder(ifExp.evaluation().eval()));
    }

   @OnlyIf ("expectError()")
   @Test (expected=InconvertibleTypesException.class)
    public void testWithExc ()
    {
        test();
    }

    @OnlyIfNot("expectError()")
    @Test
    public  void testWithoutExc()
    {
        test();
    }

    public boolean expectError ()
    {
        return expected == null;
    }

    private static Expression getExp (AkType type, boolean tf)
    {
        switch (type)
        {
            case BOOL:      return LiteralExpression.forBool(tf);
            case DECIMAL:   return new LiteralExpression(type, tf? BigDecimal.ONE: BigDecimal.ZERO);
            case U_BIGINT:  return new LiteralExpression(type, tf? BigInteger.ONE: BigInteger.ZERO);
            case FLOAT:
            case U_FLOAT:   return new LiteralExpression(type, tf ? 1.0f : 0.0f);
            case U_DOUBLE:
            case DOUBLE:    return new LiteralExpression(type, tf? 1.0 : 0.0);
            case DATE:
            case TIME:
            case DATETIME:
            case YEAR:
            case TIMESTAMP:
            case INT:
            case U_INT:
            case INTERVAL_MILLIS:
            case INTERVAL_MONTH:
            case LONG:      return new LiteralExpression(type, tf? 1L : 0L);
            case NULL:      return  LiteralExpression.forNull();
            case VARCHAR:
            case TEXT:      return new LiteralExpression(type, tf? "true" : "0");
            default:        return LiteralExpression.forNull();
        }
    }               
}
