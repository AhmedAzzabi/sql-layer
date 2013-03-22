
package com.akiban.server.types.util;

import com.akiban.qp.operator.Cursor;
import com.akiban.server.Quote;
import com.akiban.server.types.AkType;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types.ValueSourceHelper;
import com.akiban.server.types.ValueSourceIsNullException;
import com.akiban.server.types.WrongValueGetException;
import com.akiban.server.types.extract.Extractors;
import com.akiban.util.AkibanAppender;
import com.akiban.util.ByteSource;
import java.math.BigDecimal;
import java.math.BigInteger;

public abstract class AbstractArithValueSource extends ValueSource
{   
    // abstract methods
    protected abstract long rawLong ();
    protected abstract double rawDouble();
    protected abstract BigDecimal rawDecimal();
    protected abstract BigInteger rawBigInteger();
    protected abstract long rawInterval();

    
    @Override
    public BigDecimal getDecimal()
    {
        check(AkType.DECIMAL);
        return rawDecimal();
    }

    @Override
    public BigInteger getUBigInt()
    {
       check(AkType.U_BIGINT);
       return rawBigInteger();
    }

    @Override
    public ByteSource getVarBinary()
    {
       throw complain(AkType.VARBINARY);
    }

    @Override
    public double getDouble()
    {
       check(AkType.DOUBLE);
       return rawDouble();
    }

    @Override
    public double getUDouble()
    {
       throw complain(AkType.U_DOUBLE);
    }

    @Override
    public float getFloat()
    {   check(AkType.FLOAT);
        return (float)rawDouble();
    }

    @Override
    public float getUFloat()
    {
        throw complain(AkType.U_FLOAT);
    }

    @Override
    public long getDate()
    {
        check (AkType.DATE);
        return rawInterval();
    }

    @Override
    public long getDateTime()
    {
        check (AkType.DATETIME);
        return rawInterval();
    }

    @Override
    public long getInt()
    {
        check(AkType.INT);
        return rawLong();
    }

    @Override
    public long getLong()
    {
        check(AkType.LONG);
        return rawLong();
    }

    @Override
    public long getTime()
    {
       check(AkType.TIME);
       return rawInterval();
    }

    @Override
    public long getTimestamp()
    {
        check(AkType.TIMESTAMP);
        return rawInterval();
    }

    @Override
    public long getInterval_Millis()
    {
        check(AkType.INTERVAL_MILLIS);
        return rawInterval();
    }

    @Override
    public long getInterval_Month()
    {
        check(AkType.INTERVAL_MONTH);
        return rawInterval();
    }

    @Override
    public long getUInt()
    {
        check(AkType.U_INT);
        return rawLong();
    }

    @Override
    public long getYear()
    {
        check(AkType.YEAR);
        return rawInterval();
    }

    @Override
    public String getString()
    {
        throw complain(AkType.VARCHAR);
    }

    @Override
    public String getText()
    {
        throw complain(AkType.TEXT);
    }

    @Override
    public boolean getBool()
    {
        throw complain(AkType.BOOL);
    }

    @Override
    public Cursor getResultSet()
    {
       throw complain(AkType.RESULT_SET);
    }

    @Override
    public void appendAsString(AkibanAppender appender, Quote quote)
    {
        if (isNull())
            appender.append("null");
        else
        {
            String asString = "";
            AkType type = getConversionType();
            switch(type)
            {
                case LONG: asString = Extractors.getLongExtractor(getConversionType()).asString(rawLong()); break;
                case DOUBLE: asString = Extractors.getDoubleExtractor().asString(rawDouble()); break;
                case DECIMAL: asString = Extractors.getDecimalExtractor().asString(rawDecimal()); break;
                case U_BIGINT: asString = Extractors.getUBigIntExtractor().asString(rawBigInteger()); break;
            }
            quote.quote(appender, type);
            quote.append(appender, asString);
            quote.quote(appender, type);
        }
    }

    // private methods

    private WrongValueGetException complain(AkType expected) {
        return new WrongValueGetException(expected, getConversionType());
    }

    protected void check (AkType t)
    {
       ValueSourceHelper.checkType(t, getConversionType());
        if (isNull())
            throw new ValueSourceIsNullException();
    } 
}
