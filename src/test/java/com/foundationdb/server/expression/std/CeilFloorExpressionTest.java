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

import com.foundationdb.junit.NamedParameterizedRunner;
import com.foundationdb.junit.Parameterization;
import com.foundationdb.junit.ParameterizationBuilder;
import com.foundationdb.server.error.WrongExpressionArityException;
import com.foundationdb.server.expression.Expression;
import com.foundationdb.server.expression.ExpressionComposer;
import com.foundationdb.server.expression.std.CeilFloorExpression.CeilFloorName;
import com.foundationdb.server.types.AkType;
import com.foundationdb.server.types.ValueSource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import junit.framework.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(NamedParameterizedRunner.class)
public class CeilFloorExpressionTest extends ComposedExpressionTestBase
{    
    private CeilFloorName funcName;
    private ExpressionComposer composer; 
    
    private static boolean alreadyExc = false;

    public CeilFloorExpressionTest(CeilFloorName name, ExpressionComposer com)
    {
        this.composer = com;
        this.funcName = name;
    }
    
    @NamedParameterizedRunner.TestParameters
    public static Collection<Parameterization> params()
    {
        ParameterizationBuilder pb = new ParameterizationBuilder();
        
        param(pb, CeilFloorName.CEIL, CeilFloorExpression.CEIL_COMPOSER);
        param(pb, CeilFloorName.FLOOR, CeilFloorExpression.FLOOR_COMPOSER);

        return pb.asList();
    }
    
    private static void param(ParameterizationBuilder pb, CeilFloorName funcName, ExpressionComposer composer)
    {
        pb.add(funcName.name(), funcName, composer);
    }

    // This is meant to work with ExprUtil.lit to quickly yield an expression
    private ValueSource sourceOfComposing(Expression e)
    {
        return compose(composer, Arrays.asList(e)).evaluation().eval();
    }
    
    @Test
    public void testIntLongAndBigInt()
    {
        alreadyExc = true;
        int intPosInput = 15, intNegInput = -18;
        long longPosInput = 22L, longNegInput = -25L;
        BigInteger bignumPosInput = new BigInteger("1234");
        
        // In this case, the input is the same as the output, so the input is in the "expected" field
        Assert.assertEquals(intPosInput, sourceOfComposing(new LiteralExpression(AkType.INT, intPosInput)).getInt());
        Assert.assertEquals(intNegInput, sourceOfComposing(new LiteralExpression(AkType.INT, intNegInput)).getInt());

        Assert.assertEquals(longPosInput, sourceOfComposing(ExprUtil.lit(longPosInput)).getLong());
        Assert.assertEquals(longNegInput, sourceOfComposing(ExprUtil.lit(longNegInput)).getLong());

        Assert.assertEquals(bignumPosInput, sourceOfComposing(new LiteralExpression(AkType.U_BIGINT, bignumPosInput)).getUBigInt());
        
        // Unsigned int (type long)
        Assert.assertEquals(longPosInput, sourceOfComposing(new LiteralExpression(AkType.U_INT, longPosInput)).getUInt());
       
    }
    
    @Test 
    public void testDouble() 
    {
        double posInput = 20.8d, negInput = -100.2d;
        // -->                                                   CEIL  : FLOOR
        double posExpected = (funcName == CeilFloorName.CEIL) ? 21.0d : 20.0d; 
        double negExpected = (funcName == CeilFloorName.CEIL) ? -100.0d : -101.0d;
        
        Assert.assertEquals(posExpected, sourceOfComposing(ExprUtil.lit(posInput)).getDouble(), 0.0001);
        Assert.assertEquals(negExpected, sourceOfComposing(ExprUtil.lit(negInput)).getDouble(), 0.0001);
        Assert.assertEquals(posExpected, sourceOfComposing(new LiteralExpression(AkType.U_DOUBLE, posInput)).getUDouble(), 0.0001);

    }
   
    @Test
    public void testFloat()
    {
        float posInput = 20.8f, negInput = -100.2f;
        // -->                                                   CEIL  : FLOOR
        float posExpected = (funcName == CeilFloorName.CEIL) ? 21.0f : 20.0f; 
        float negExpected = (funcName == CeilFloorName.CEIL) ? -100.0f : -101.0f;
        
        Assert.assertEquals(posExpected, sourceOfComposing(new LiteralExpression(AkType.FLOAT, posInput)).getFloat(), 0.0001);
        Assert.assertEquals(negExpected, sourceOfComposing(new LiteralExpression(AkType.FLOAT, negInput)).getFloat(), 0.0001);
        Assert.assertEquals(posExpected, sourceOfComposing(new LiteralExpression(AkType.U_FLOAT, posInput)).getUFloat(), 0.0001);

    }
    
    @Test
    public void testDecimal()
    {
        BigDecimal posInput = new BigDecimal("1234.567"), negInput = new BigDecimal("-543.211");
        // -->                                                             --CEIL--        :       --FLOOR--
        BigDecimal posExpected = (funcName == CeilFloorName.CEIL) ? new BigDecimal("1235") : new BigDecimal("1234"); 
        BigDecimal negExpected = (funcName == CeilFloorName.CEIL) ? new BigDecimal("-543") : new BigDecimal("-544");
        
        // NOTE: BigDecimal comparison with .equals() checks if the scale is the same too, so we use compareTo()
        Assert.assertEquals(0, posExpected.compareTo(sourceOfComposing(new LiteralExpression(AkType.DECIMAL, posInput)).getDecimal()));
        Assert.assertEquals(0, negExpected.compareTo(sourceOfComposing(new LiteralExpression(AkType.DECIMAL, negInput)).getDecimal()));        
    }
    
    @Test (expected=WrongExpressionArityException.class)
    public void testArity ()
    {
        compose(composer, Arrays.asList(ExprUtil.lit(1), ExprUtil.lit(2)));
    }
    
    @Test
    public void testNull ()
    {
        AkType testType = AkType.NULL;
        Expression output = compose(composer, Arrays.asList(new LiteralExpression(AkType.NULL, null)));
        
        ValueSource shouldBeNullValueSource = output.evaluation().eval();
        Assert.assertTrue(funcName.name() + " value source should be NULL", shouldBeNullValueSource.isNull());
        Assert.assertEquals(funcName.name() + " output should be type NULL", testType, output.valueType());  
    }
    
    @Test
    public void testInfinity()
    {
        AkType testType = AkType.DOUBLE;
        ValueSource posOutput = sourceOfComposing(new LiteralExpression(testType, Double.POSITIVE_INFINITY));
        ValueSource negOutput = sourceOfComposing(new LiteralExpression(testType, Double.NEGATIVE_INFINITY));
        
        double shouldBePosInfinity = posOutput.getDouble();
        double shouldBeNegInfinity = negOutput.getDouble();
        
        Assert.assertTrue(Double.isInfinite(shouldBePosInfinity));
        Assert.assertTrue(Double.isInfinite(shouldBeNegInfinity));
    }    
  
    @Override
    protected CompositionTestInfo getTestInfo()
    {
        return new CompositionTestInfo(1, AkType.DOUBLE, true);
    }

    @Override
    protected ExpressionComposer getComposer()
    {
        return this.composer;
    }

    @Override
    public boolean alreadyExc()
    {
        return alreadyExc;
    }

}
