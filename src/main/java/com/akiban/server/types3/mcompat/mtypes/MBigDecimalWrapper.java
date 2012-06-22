/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */

package com.akiban.server.types3.mcompat.mtypes;

import com.akiban.server.types3.common.BigDecimalWrapper;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class MBigDecimalWrapper implements BigDecimalWrapper {

    private BigDecimal value;

    public MBigDecimalWrapper(String num)
    {
        value = new BigDecimal(num);
    }

    public MBigDecimalWrapper(long val)
    {
        value = BigDecimal.valueOf(val);
    }

    public MBigDecimalWrapper()
    {
        value = BigDecimal.ZERO;
    }
    
    @Override
    public void reset() {
        value = BigDecimal.ZERO;
    }
            
    @Override
    public BigDecimalWrapper add(BigDecimalWrapper other) {
        MBigDecimalWrapper o = (MBigDecimalWrapper) other;
        value = value.add(o.value);
        return this;
    }

    @Override
    public BigDecimalWrapper subtract(BigDecimalWrapper other) {
        MBigDecimalWrapper o = (MBigDecimalWrapper) other;
        value = value.subtract(o.value);
        return this;
    }

    @Override
    public BigDecimalWrapper multiply(BigDecimalWrapper other) {
        MBigDecimalWrapper o = (MBigDecimalWrapper) other;
        value = value.multiply(o.value);
        return this;
    }

    @Override
    public BigDecimalWrapper divide(BigDecimalWrapper other) {
        MBigDecimalWrapper o = (MBigDecimalWrapper) other;
        value = value.divide(o.value);
        return this;
    }
    
    @Override
    public int getSign() {
        return value.signum();
    }
    
    @Override
    public BigDecimalWrapper divide(BigDecimalWrapper augend, int scale)
    {
        value = value.divide(((MBigDecimalWrapper)augend).value,
                scale,
                RoundingMode.HALF_UP);
        return this;
    }

    @Override
    public BigDecimalWrapper abs()
    {
        value = value.abs();
        return this;
    }
    
    public int getScale()
    {
        return value.scale();
    }

    @Override
    public int getPrecision()
    {
        return value.precision();
    }

    @Override
    public BigDecimalWrapper parseString(String num)
    {
        value = new BigDecimal (num);
        return this;
    }

    @Override
    public int compareTo(Object o)
    {
        if (o == null)
            return 1;
        
        return value.compareTo(((MBigDecimalWrapper)o).value);
    }

    @Override
    public BigDecimalWrapper round(int precision, int scale)
    {
        value = value.round(new MathContext(precision, RoundingMode.HALF_UP));
        return this;
    }

    @Override
    public BigDecimalWrapper negate()
    {
        value = value.negate();
        return this;
    }
}
