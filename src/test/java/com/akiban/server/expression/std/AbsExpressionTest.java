
package com.akiban.server.expression.std;

import com.akiban.server.expression.Expression;
import com.akiban.server.expression.ExpressionComposer;
import com.akiban.server.types.AkType;
import com.akiban.server.types.NullValueSource;
import com.akiban.server.types.ValueSource;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.Assert;
import org.junit.Test;


public class AbsExpressionTest extends ComposedExpressionTestBase
{
    // Test both positive and negative values
    @Test
    public void testDouble()
    {
        AkType testType = AkType.DOUBLE;
        Expression posOutput = new AbsExpression(new LiteralExpression(testType, 10.456));
        Expression negOutput = new AbsExpression(new LiteralExpression(testType, -2.23));
        
        double absValueOfPos = posOutput.evaluation().eval().getDouble();
        double absValueOfNeg = negOutput.evaluation().eval().getDouble();
        
        Assert.assertEquals("ABS posOutput should be DOUBLE", testType, posOutput.valueType());
        Assert.assertEquals("ABS negOutput should be DOUBLE", testType, negOutput.valueType());
        Assert.assertEquals(10.456, absValueOfPos, 0.0001);
        Assert.assertEquals(2.23, absValueOfNeg, 0.0001);      
    }
    
    @Test
    public void testFloat()
    {
        AkType testType = AkType.FLOAT;
        Expression posOutput = new AbsExpression(new LiteralExpression(testType, 10.456f));
        Expression negOutput = new AbsExpression(new LiteralExpression(testType, -2.23f));

        float absValueOfPos = posOutput.evaluation().eval().getFloat();
        float absValueOfNeg = negOutput.evaluation().eval().getFloat();
        
        Assert.assertEquals("ABS posOutput should be FLOAT", testType, posOutput.valueType());
        Assert.assertEquals("ABS negOutput should be FLOAT", testType, negOutput.valueType());
        Assert.assertEquals(10.456f, absValueOfPos, 0.0001f);
        Assert.assertEquals(2.23f, absValueOfNeg, 0.0001f);      
    }
    
    // No test for negative value since U_BIGINT always returns a positive value
    @Test
    public void testBignum()
    {
        AkType testType = AkType.U_BIGINT;
        BigInteger posLiteral = new BigInteger("123454321");
        Expression posOutput = new AbsExpression(new LiteralExpression(testType, posLiteral) );       
        BigInteger absValueOfPos = posOutput.evaluation().eval().getUBigInt();

        Assert.assertEquals("ABS posOutput should be BIGINT", testType, posOutput.valueType());
        Assert.assertEquals(posLiteral, absValueOfPos);
    }
    
    @Test
    public void testUnsignedInt()
    {
        AkType testType = AkType.U_INT;
        long posLiteral = 40;
        Expression posOutput = new AbsExpression(new LiteralExpression(testType, posLiteral) );       
        long absValueOfPos = posOutput.evaluation().eval().getUInt();

        Assert.assertEquals("ABS posOutput should be UINT", testType, posOutput.valueType());
        Assert.assertEquals(posLiteral, absValueOfPos);
    }
    
    @Test
    public void testUnsignedFloat()
    {
        AkType testType = AkType.U_FLOAT;
        float posLiteral = 23.22f;
        Expression posOutput = new AbsExpression(new LiteralExpression(testType, posLiteral) );       
        float absValueOfPos = posOutput.evaluation().eval().getUFloat();

        Assert.assertEquals("ABS posOutput should be UFLOAT", testType, posOutput.valueType());
        Assert.assertEquals(posLiteral, absValueOfPos, 0.0001);
    }
        
    @Test
    public void testUnsignedDouble()
    {
        AkType testType = AkType.U_DOUBLE;
        double posLiteral = 23.22;
        Expression posOutput = new AbsExpression(new LiteralExpression(testType, posLiteral) );       
        double absValueOfPos = posOutput.evaluation().eval().getUDouble();

        Assert.assertEquals("ABS posOutput should be UDOUBLE", testType, posOutput.valueType());
        Assert.assertEquals(posLiteral, absValueOfPos, 0.0001);
    }
    
    @Test
    public void testDecimal()
    {
        AkType testType = AkType.DECIMAL;
        BigDecimal posLiteral = new BigDecimal("123454321.123"), negLiteral = new BigDecimal("-123454321.123");
        
        Expression posOutput = new AbsExpression(new LiteralExpression(testType, posLiteral) );
        Expression negOutput = new AbsExpression(new LiteralExpression(testType, negLiteral) );
        
        BigDecimal absValueOfPos = posOutput.evaluation().eval().getDecimal();
        BigDecimal absValueOfNeg = negOutput.evaluation().eval().getDecimal();

        Assert.assertEquals("ABS posOutput should be DECIMAL", testType, posOutput.valueType());
        Assert.assertEquals("ABS negOutput should be DECIMAL", testType, negOutput.valueType());
        Assert.assertEquals(posLiteral, absValueOfPos);
        Assert.assertEquals(negLiteral.negate(), absValueOfNeg);   
    }
    
    @Test
    public void testLong()
    {
        AkType testType = AkType.LONG;
        Expression posOutput = new AbsExpression(new LiteralExpression(testType, 1234));
        Expression negOutput = new AbsExpression(new LiteralExpression(testType, -4321));
        
        long absValueOfPos = posOutput.evaluation().eval().getLong();
        long absValueOfNeg = negOutput.evaluation().eval().getLong();
        
        Assert.assertEquals("ABS posOutput should be LONG", testType, posOutput.valueType());
        Assert.assertEquals("ABS negOutput should be LONG", testType, negOutput.valueType());
        Assert.assertEquals(1234, absValueOfPos);
        Assert.assertEquals(4321, absValueOfNeg); 
    }
        
    @Test
    public void testInt()
    {
        AkType testType = AkType.INT;
        Expression posOutput = new AbsExpression(new LiteralExpression(testType, 1234));
        Expression negOutput = new AbsExpression(new LiteralExpression(testType, -4321));
        
        // getInt() still returns a Java long
        long absValueOfPos = posOutput.evaluation().eval().getInt();
        long absValueOfNeg = negOutput.evaluation().eval().getInt();

        Assert.assertEquals("ABS posOutput should be INT", testType, posOutput.valueType());
        Assert.assertEquals("ABS negOutput should be INT", testType, negOutput.valueType());
        Assert.assertEquals(1234, absValueOfPos);
        Assert.assertEquals(4321, absValueOfNeg); 
    }
    
    @Test
    public void testNull()
    {
        AkType testType = AkType.NULL;
       Expression output = new AbsExpression(new LiteralExpression(AkType.NULL, null));
        
        ValueSource shouldBeNullValueSource = output.evaluation().eval();
        
        Assert.assertEquals("ABS value source should be NULL", NullValueSource.only(), shouldBeNullValueSource);
        Assert.assertEquals("ABS output should be NULL", testType, output.valueType());
    }
    
    @Test
    public void testInfinity()
    {
        AkType testType = AkType.DOUBLE;
        Expression posOutput = new AbsExpression(new LiteralExpression(testType, Double.POSITIVE_INFINITY));
        Expression negOutput = new AbsExpression(new LiteralExpression(testType, Double.NEGATIVE_INFINITY));
        
        double absValueOfPos = posOutput.evaluation().eval().getDouble();
        double absValueOfNeg = negOutput.evaluation().eval().getDouble();
        
        Assert.assertEquals("ABS posOutput should be DOUBLE", testType, posOutput.valueType());
        Assert.assertEquals("ABS negOutput should be DOUBLE", testType, negOutput.valueType());
        Assert.assertEquals(Double.POSITIVE_INFINITY, absValueOfPos, 0.0001);
        Assert.assertEquals(Double.POSITIVE_INFINITY, absValueOfNeg, 0.0001);    
    }
    
    @Override
    protected CompositionTestInfo getTestInfo()
    {
        // Put down AkType.DOUBLE but can be any numeric type
        return new CompositionTestInfo(1, AkType.DOUBLE, true);
    }

    @Override
    protected ExpressionComposer getComposer()
    {
        return AbsExpression.COMPOSER;
    }

    @Override
    protected boolean alreadyExc()
    {
        return false;
    }
    
}
