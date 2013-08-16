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

import java.math.BigDecimal;
import java.text.DateFormatSymbols;
import java.util.Locale;
import com.foundationdb.server.types.ValueSource;
import com.foundationdb.server.expression.ExpressionComposer;
import com.foundationdb.server.expression.Expression;
import com.foundationdb.server.types.AkType;
import com.foundationdb.server.types.extract.Extractors;
import java.util.Arrays;
import org.joda.time.DateTime;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExtractExpressionTest extends ComposedExpressionTestBase
{
    private static final CompositionTestInfo testInfo = new CompositionTestInfo(1, AkType.DATE, true);
      
    @Test
    public void testNull() // test null for Extract functions
    {
        for (ExtractExpression.TargetExtractType type : ExtractExpression.TargetExtractType.values())
            assertTrue("ValueSource is NULL", new ExtractExpression(LiteralExpression.forNull(), type).evaluation().eval().isNull());        
    }
    
    //-----------------------------LAST DAY------------------------------------    
    @Test
    public void quarter()
    {        
        testAndCheck("2009-01-01", 1);
        testAndCheck("2009-03-31", 1);
        testAndCheck("2009-04-01", 2);
        testAndCheck("2009-06-25", 2);
        testAndCheck("2009-07-01", 3);
        testAndCheck("2009-09-30", 3);
        testAndCheck("2009-10-01", 4);
        testAndCheck("2009-12-01", 4);
    }
    
    private static void testAndCheck (String date, int expected)
    {        
     
        Expression in = new LiteralExpression(AkType.DATE, Extractors.getLongExtractor(AkType.DATE).getLong(date));        
        Expression top = compose(ExtractExpression.QUARTER_COMPOSER, Arrays.asList(in));
        
        assertEquals("QUATER(" + date + "): ", expected, top.evaluation().eval().getInt());        
    }
    
    //-----------------------------LAST DAY------------------------------------
    @Test
    public void testDayOfYearFromDecimal()
    {
        Expression arg = new LiteralExpression(AkType.DECIMAL, BigDecimal.valueOf(20081231.5d));
        Expression top = compose(ExtractExpression.DAY_YEAR_COMPOSER, Arrays.asList(arg));
        
        assertTrue("Top should be null: ", top.evaluation().eval().isNull());
    }

    @Test
    public void testLastDayNull()
    {
        Expression in = ExprUtil.lit(20091231.6);
        Expression top = compose(ExtractExpression.LAST_DAY_COMPOSER, Arrays.asList(in));
        
        assertTrue("LAST_DAY(20091231.6) should be NULL", top.evaluation().eval().isNull());
    }
    @Test
    public void lastDay()
    {
        for (int yr : new int[]{2000, 1900, 2001, 2002, 2003, 2004})
            for (int month = 1; month < 13; ++month)
            {
                Expression in = new LiteralExpression(AkType.DATE, Extractors.getLongExtractor(AkType.DATE).getLong(yr + "-" + month + "-12"));
                Expression top = compose(ExtractExpression.LAST_DAY_COMPOSER, Arrays.asList(in));
                DateTime datetime = DateTime.parse(Extractors.getStringExtractor().getObject(in.evaluation().eval()));
                assertEquals("Last day of Month: " + month,
                              Extractors.getLongExtractor(AkType.DATE).getLong( yr + "-" + month + "-" + datetime.dayOfMonth().getMaximumValue()) ,
                              top.evaluation().eval().getDate());
            }
    }
    //-----------------------------MONTHNAME------------------------------------
    @Test
    public void monthName()
    {
        for (int month = 0; month < 12; ++month)
        {
            Expression in = new LiteralExpression(AkType.DATE, Extractors.getLongExtractor(AkType.DATE).getLong("2009-" + (month+1) + "-12"));
            Expression top = compose(ExtractExpression.MONTH_NAME_COMPOSER, Arrays.asList(in));

            assertEquals(new DateFormatSymbols(new Locale(System.getProperty("user.language"))).getMonths()[month],
                          top.evaluation().eval().getString());
        }
    }
        
    // --------------------------- GET DAY OF YEAR -----------------------------
    private static final Long [] outputs = {1L, 365L, 1L, 366L, 365L, null, null, null, 1L, 365L};
    private static final String[] inputs = {"2009-01-01 12:30:10", "2009-12-31 00:14:12",
                    "2008-01-01 14:59:12", "2008-12-31 13:43:24", "1900-12-31 00:12:12",
                    "2009-13-12 12:30:10", "2009-11-31 11:12:13", "1900-02-29 11:12:30", // invalid dates
                    "10000-01-01 12:30:10", "999-12-31 12:30:10"}; // invalid mysql-dates, which
                                                                   // are not considered invalid here
    
    // Because ExtractorsForDates.TIMESTAMP does bound-checking, some exception 
    // would've been thrown before the invalid date got to ExtractException.
    // Thus testing for dayOfYear(TIMESTAMP([invalid date])) is impossible and unnecessary.
    private static final String[] inputsForTimestamp = {"2009-01-01 12:30:10", "2009-12-31 00:14:12",
                    "2008-01-01 14:59:12", "2008-12-31 13:43:24", "1900-12-31 00:12:12"};
    
    @Test
    public void getDayOfYearFromDate ()
    {
        for (int n = 0; n < outputs.length; ++n)        
            testDayOfYear(inputs[n].split("\\s+")[0], AkType.DATE, outputs[n]);
    }

    @Test
    public void getDayOfYearFromDateTimeStamp ()
    {
        for (int n = 0; n < inputs.length; ++n)
            testDayOfYear(inputs[n], AkType.DATETIME, outputs[n]);
        
    }
    
    @Test
    public void getDayOfYearFromTimestamp ()
    {
        for (int n = 0; n < inputsForTimestamp.length; ++n)
            testDayOfYear(inputsForTimestamp[n], AkType.TIMESTAMP, outputs[n]);
    }

    @Test
    public void getDayOfYearFromYear ()
    {
        testDayOfYear("2009", AkType.YEAR, null);
    }

    @Test
    public void getDayOfYearFromTime ()
    {
        testDayOfYear("12:30:10", AkType.TIME, null);
    }

    // --------------------------- GET DATE-------------------------------------
    @Test
    public void testBound ()
    {
        Expression arg = new LiteralExpression(AkType.LONG, 9999999L);
        Expression top = getTopExp(ExtractExpression.DATE_COMPOSER,arg);

        assertTrue("input 9999999L, source should be null", top.evaluation().eval().isNull());
    }

    @Test
    public void getDateFromDate()
    {
        Expression arg = new LiteralExpression(AkType.DATE, 1234L);
        Expression top = compose(ExtractExpression.DATE_COMPOSER, Arrays.asList(arg));

        assertEquals(1234L, top.evaluation().eval().getDate());
    }

    @Test
    public void getDateFromDateTime()
    {
        Expression top = getTopExp(ExtractExpression.DATE_COMPOSER, getDatetime());

        assertEquals("2009-08-30", Extractors.getLongExtractor(AkType.DATE).asString(top.evaluation().eval().getDate()));
    }

    @Test
    public void getDateFromTimestamp()
    {
        Expression top = getTopExp(ExtractExpression.DATE_COMPOSER, getTimestamp());
        assertEquals("2009-08-30", Extractors.getLongExtractor(AkType.DATE).asString(top.evaluation().eval().getDate()));
    }

    @Test
    public void getDateFromVarchar()
    {
        Expression arg = new LiteralExpression(AkType.VARCHAR, "2009-12-30 12:30:10");
        Expression top = getTopExp(ExtractExpression.DATE_COMPOSER, arg);

        assertEquals("2009-12-30", Extractors.getLongExtractor(AkType.DATE).asString(top.evaluation().eval().getDate()));
    }

    @Test
    public void getDateFromBadString()
    {
        Expression arg = new LiteralExpression(AkType.VARCHAR, "2009-30-30 12:30:10");
        Expression top = getTopExp(ExtractExpression.DATE_COMPOSER, arg);

        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getDateFromLong()
    {
        Expression arg = new LiteralExpression(AkType.LONG, 20091230123045L);
        Expression top = getTopExp(ExtractExpression.DATE_COMPOSER, arg);

        assertEquals("2009-12-30", Extractors.getLongExtractor(AkType.DATE).asString(top.evaluation().eval().getDate()));

    }

    @Test
    public void getDateFromDouble()
    {
        Expression arg = new LiteralExpression(AkType.DOUBLE, 20091212.5);
        Expression top = getTopExp(ExtractExpression.DATE_COMPOSER, arg);

        assertEquals("DATE(20091212.5) ", 
                     "2009-12-13", 
                     Extractors.getLongExtractor(AkType.DATE).asString(top.evaluation().eval().getDate()));
    }

    @Test
    public void getDateFromYear()
    {
        Expression top = getTopExp(ExtractExpression.DATE_COMPOSER, getYear());
        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getDateFromNull ()
    {
        Expression top = getTopExp(ExtractExpression.DATE_COMPOSER, LiteralExpression.forNull());
        assertTrue (top.evaluation().eval().isNull());
    }

    //------------------------GET DATETIME--------------------------------------
    @Test
    public void testBoundDateTime()
    {
        Expression arg = new LiteralExpression(AkType.LONG, 99);
        Expression top = getTopExp(ExtractExpression.DATETIME_COMPOSER, arg);

        assertTrue("Top should be null", top.evaluation().eval().isNull());
    }

    @Test
    public void getDateTimeBug () //  bug 905525 - unit test passes
    {
        Expression timeStamp = new LiteralExpression(AkType.TIMESTAMP,
                Extractors.getLongExtractor(AkType.TIMESTAMP).getLong("1999-12-31 01:15:33"));
        Expression top = compose(ExtractExpression.DATETIME_COMPOSER, Arrays.asList(timeStamp));

        String actual = Extractors.getLongExtractor(AkType.DATETIME).asString(top.evaluation().eval().getDateTime());
        assertEquals(actual, "1999-12-31 01:15:33");
    }

    @Test
    public void getDateTimeZeroYear ()
    {
        testWeirdDateTime("0000-01-01", "0000-01-01 00:00:00");
    }

    @Test
    public void getDateTimeZeroMonth ()
    {
        testWeirdDateTime("0001-00-01", "0001-00-01 00:00:00");
    }

    @Test
    public void getDateTimeZeroDay ()
    {
        testWeirdDateTime("0001-01-00", "0001-01-00 00:00:00");
    }

    @Test
    public void getDateTimeZero ()
    {
        testWeirdDateTime("0000-00-00", "0000-00-00 00:00:00");
    }

    private void testWeirdDateTime(String input, String exp)
    {
        Expression top = getTop(input, ExtractExpression.DATETIME_COMPOSER);
        String actual = Extractors.getLongExtractor(AkType.DATETIME).asString(top.evaluation().eval().getDateTime());
        assertEquals(exp, actual);
    }



    @Test
    public void getDatetimeFromDate()
    {
        Expression top = getTopExp(ExtractExpression.DATETIME_COMPOSER, getDate());

        long s = top.evaluation().eval().getDateTime();

        assertEquals(20090830000000L, s);
    }

    @Test
    public void getDatetimeFromDateTime()
    {
        Expression top = getTopExp(ExtractExpression.DATETIME_COMPOSER, getDatetime());

        assertEquals(20090830113045L, top.evaluation().eval().getDateTime());
    }

    @Test
    public void getDatetimeFromTimestamp()
    {
        Expression top = getTopExp(ExtractExpression.DATETIME_COMPOSER, getTimestamp());
        assertEquals(20090830113045L, top.evaluation().eval().getDateTime());
    }


    @Test
    public void getDatetimeFromVarchar()
    {
        Expression arg = new LiteralExpression(AkType.VARCHAR, "2009-12-30 12:30:10");
        Expression top = getTopExp(ExtractExpression.DATETIME_COMPOSER, arg);

        assertEquals("2009-12-30 12:30:10", Extractors.getLongExtractor(AkType.DATETIME).asString(top.evaluation().eval().getDateTime()));
    }

    @Test
    public void getDatetimeFromBadString()
    {
        Expression arg = new LiteralExpression(AkType.VARCHAR, "2009-30-30 12:30:10");
        Expression top = getTopExp(ExtractExpression.DATETIME_COMPOSER, arg);

        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getDatetimeFromLong()
    {
        Expression arg = new LiteralExpression(AkType.LONG, 20091230123045L);
        Expression top = getTopExp(ExtractExpression.DATETIME_COMPOSER, arg);

        assertEquals(20091230123045L,top.evaluation().eval().getDateTime());

    }

    @Test
    public void getDateTimeFromDouble()
    {
        Expression arg = new LiteralExpression(AkType.DOUBLE, 2345.5);
        Expression top = getTopExp(ExtractExpression.DATETIME_COMPOSER, arg);

        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getDatetimeFromYear()
    {
        Expression top = getTopExp(ExtractExpression.DATETIME_COMPOSER, getYear());
        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getDatetimeFromNull ()
    {
        Expression top = getTopExp(ExtractExpression.DATETIME_COMPOSER, LiteralExpression.forNull());
        assertTrue (top.evaluation().eval().isNull());
    }
    //----------------------- GET DAY-------------------------------------------
    @Test
    public void testBoundDay ()
    {
        Expression arg = new LiteralExpression(AkType.LONG, 99);
        Expression top = getTopExp(ExtractExpression.DAY_COMPOSER, arg);

        assertTrue("Top should be null", top.evaluation().eval().isNull());
    }

    @Test
    public void getDayZero ()
    {
        Expression top = getTop("0000-00-00", ExtractExpression.DAY_COMPOSER);
        assertEquals(0L, top.evaluation().eval().getInt());
    }

    @Test
    public void getDayFromDate()
    {
        Expression top = getTopExp(ExtractExpression.DAY_COMPOSER, getDate());
        assertEquals(30L, top.evaluation().eval().getInt());

    }

    @Test
    public void getDayFromDateTime()
    {
        Expression top = getTopExp(ExtractExpression.DAY_COMPOSER, getDatetime());
        assertEquals(30L, top.evaluation().eval().getInt());
    }

    @Test
    public void getDayFromTime()
    {
        Expression top = getTopExp(ExtractExpression.DAY_COMPOSER, getTime());
        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getDayFromTimestamp()
    {
        Expression top = getTopExp(ExtractExpression.DAY_COMPOSER, getTimestamp());
        assertEquals(30L, top.evaluation().eval().getInt());
    }

    @Test
    public void getDayFromVarchar()
    {
        Expression arg = new LiteralExpression(AkType.VARCHAR, "2009-12-30 12:30:10");
        Expression top = getTopExp(ExtractExpression.DAY_COMPOSER, arg);

        assertEquals(30L, top.evaluation().eval().getInt());
    }

    @Test
    public void getDayFromBadString()
    {
        Expression arg = new LiteralExpression(AkType.VARCHAR, "2009-30-30 12:30:10");
        Expression top = getTopExp(ExtractExpression.DAY_COMPOSER, arg);

        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getDayFromLong()
    {
        Expression arg = new LiteralExpression(AkType.LONG, 20091230123045L);
        Expression top = getTopExp(ExtractExpression.DAY_COMPOSER, arg);

        assertEquals(30L, top.evaluation().eval().getInt());
    }

    @Test
    public void getDayFromDouble()
    {
        Expression arg = new LiteralExpression(AkType.DOUBLE, 20091212.5);
        Expression top = getTopExp(ExtractExpression.DAY_COMPOSER, arg);

        assertEquals("DAY(20091212.5) ", 13, top.evaluation().eval().getInt());
    }

    @Test
    public void getDayFromYear()
    {
        Expression top = getTopExp(ExtractExpression.DATE_COMPOSER, getYear());
        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getDayFromNull ()
    {
        Expression top = getTopExp(ExtractExpression.DAY_COMPOSER, LiteralExpression.forNull());
        assertTrue (top.evaluation().eval().isNull());
    }

    // ------------------------ GET HOUR (or HOUR_OF_DAY)-----------------------
    @Test
    public void getHourFromChar ()
    {
        Expression str = new LiteralExpression(AkType.VARCHAR, "A");
        Expression top = compose(ExtractExpression.HOUR_COMPOSER, Arrays.asList(str));

        assertTrue("top should be null", top.evaluation().eval().isNull());
    }
    @Test
    public void getHourFrom3DigitHrTime ()
    {
        Expression time = new LiteralExpression (AkType.TIME, Extractors.getLongExtractor(AkType.TIME).getLong("-999:12:20"));
        Expression top = compose(ExtractExpression.HOUR_COMPOSER, Arrays.asList(time));

        assertEquals("expected 999", 999, top.evaluation().eval().getInt());
    }

    @Test
    public void getHourFromDate()
    {
        Expression top = getTopExp(ExtractExpression.HOUR_COMPOSER, getDate());
        assertEquals(0L, top.evaluation().eval().getInt());
    }

    @Test
    public void getHourFromDateTime()
    {
        Expression top = getTopExp(ExtractExpression.HOUR_COMPOSER, getDatetime());
        assertEquals(11L, top.evaluation().eval().getInt());
    }

    @Test
    public void getHourFromTime()
    {
        Expression top = getTopExp(ExtractExpression.HOUR_COMPOSER, getTime());
        assertEquals(11L, top.evaluation().eval().getInt());
    }

    @Test
    public void getHourFromTimestamp()
    {
        Expression top = getTopExp(ExtractExpression.HOUR_COMPOSER, getTimestamp());
        assertEquals(11L, top.evaluation().eval().getInt());
    }

    @Test
    public void getHourFromVarchar()
    {
        Expression arg = new LiteralExpression(AkType.VARCHAR, "2009-12-30 12:30:10");
        Expression top = getTopExp(ExtractExpression.HOUR_COMPOSER, arg);

        assertEquals(12L, top.evaluation().eval().getInt());
    }

    @Test
    public void getHourFromBadString()
    {
        Expression arg = new LiteralExpression(AkType.VARCHAR, "2009-30-30 12:30:10");
        Expression top = getTopExp(ExtractExpression.HOUR_COMPOSER, arg);

        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getHourFromLong()
    {
        Expression arg = new LiteralExpression(AkType.LONG, 20091230123045L);
        Expression top = getTopExp(ExtractExpression.HOUR_COMPOSER, arg);

        assertEquals(12L, top.evaluation().eval().getInt());
    }

    @Test
    public void getHourFromDouble()
    {
        Expression arg = new LiteralExpression(AkType.DOUBLE, 2345.5);
        Expression top = getTopExp(ExtractExpression.HOUR_COMPOSER, arg);

        assertEquals("HOUR(2345.5) ", 0, top.evaluation().eval().getInt());
    }

    @Test
    public void getHourFromYear()
    {
        Expression top = getTopExp(ExtractExpression.HOUR_COMPOSER, getYear());
        assertEquals(0L, top.evaluation().eval().getInt());
    }

    @Test
    public void getHourFromNull ()
    {
        Expression top = getTopExp(ExtractExpression.HOUR_COMPOSER, LiteralExpression.forNull());
        assertTrue (top.evaluation().eval().isNull());
    }
    //-----------------------------GET MINUTE-----------------------------------
    @Test
    public void getMinuteFrom999 ()
    {
        Expression top = getTopExp(ExtractExpression.MINUTE_COMPOSER, new LiteralExpression(AkType.LONG, 999999L));
        assertTrue("Top should be null", top.evaluation().eval().isNull());
    }

    @Test
    public void getMinuteFromDate()
    {
        Expression top = getTopExp(ExtractExpression.MINUTE_COMPOSER, getDate());
        assertEquals(0L, top.evaluation().eval().getInt());
    }

    @Test
    public void getMinuteFromDateTime()
    {
        Expression top = getTopExp(ExtractExpression.MINUTE_COMPOSER, getDatetime());
        assertEquals(30L, top.evaluation().eval().getInt());
    }

    @Test
    public void getMinuteFromTime()
    {
        Expression top = getTopExp(ExtractExpression.MINUTE_COMPOSER, getTime());
        assertEquals(30L, top.evaluation().eval().getInt());
    }

    @Test
    public void getMinuteFromTimestamp()
    {
        Expression top = getTopExp(ExtractExpression.MINUTE_COMPOSER, getTimestamp());
        assertEquals(30L, top.evaluation().eval().getInt());
    }

    @Test
    public void getMinuteFromVarchar()
    {
        Expression arg = new LiteralExpression(AkType.VARCHAR, "2009-12-30 12:30:10");
        Expression top = getTopExp(ExtractExpression.MINUTE_COMPOSER, arg);

        assertEquals(30L, top.evaluation().eval().getInt());
    }

    @Test
    public void getMinuteFromBadString()
    {
        Expression arg = new LiteralExpression(AkType.VARCHAR, "2009-30-30 12:30:10");
        Expression top = getTopExp(ExtractExpression.MINUTE_COMPOSER, arg);

        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getMinuteFromLong()
    {
        Expression arg = new LiteralExpression(AkType.LONG, 20091230123045L);
        Expression top = getTopExp(ExtractExpression.MINUTE_COMPOSER, arg);

        assertEquals(30L, top.evaluation().eval().getInt());
    }

    @Test
    public void getMinuteFromDouble()
    {
        Expression arg = new LiteralExpression(AkType.DOUBLE, 2345.5);
        Expression top = getTopExp(ExtractExpression.MINUTE_COMPOSER, arg);

        assertEquals("MINUTE(2345.5) ", 23, top.evaluation().eval().getInt());
    }

    @Test
    public void getMinuteFromYear()
    {
        Expression top = getTopExp(ExtractExpression.MINUTE_COMPOSER, getYear());
        assertEquals(0L, top.evaluation().eval().getInt());
    }

    @Test
    public void getMinuteFromNull ()
    {
        Expression top = getTopExp(ExtractExpression.MINUTE_COMPOSER, LiteralExpression.forNull());
        assertTrue (top.evaluation().eval().isNull());
    }
    // ----------------------- GET MONTH----------------------------------------
    @Test
    public void testBoundMonth ()
    {
        Expression arg = new LiteralExpression(AkType.LONG, 999L);
        Expression top = getTopExp(ExtractExpression.MONTH_COMPOSER, arg);

        assertTrue("Top should be null", top.evaluation().eval().isNull());
    }
    @Test
    public void getMonthZero()
    {
        Expression top = getTop("0000-00-01", ExtractExpression.MONTH_COMPOSER);
        assertEquals(0L, top.evaluation().eval().getInt());
    }

    @Test
    public void getMonthFromDate()
    {
        Expression top = getTopExp(ExtractExpression.MONTH_COMPOSER, getDate());
        assertEquals(8L, top.evaluation().eval().getInt());

    }

    @Test
    public void getMonthFromDateTime()
    {
        Expression top = getTopExp(ExtractExpression.MONTH_COMPOSER, getDatetime());
        assertEquals(8L, top.evaluation().eval().getInt());
    }

    @Test
    public void getMonthFromTime()
    {
        Expression top = getTopExp(ExtractExpression.MONTH_COMPOSER, getTime());
        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getMonthFromTimestamp()
    {
        Expression top = getTopExp(ExtractExpression.MONTH_COMPOSER, getTimestamp());
        assertEquals(8L, top.evaluation().eval().getInt());
    }

    @Test
    public void getMonthFromVarchar()
    {
        Expression arg = new LiteralExpression(AkType.VARCHAR, "2009-08-30 12:30:10");
        Expression top = getTopExp(ExtractExpression.MONTH_COMPOSER, arg);

        assertEquals(8L, top.evaluation().eval().getInt());
    }

    @Test
    public void getMonthFromBadString()
    {
        Expression arg = new LiteralExpression(AkType.VARCHAR, "2009-30-30 12:30:10");
        Expression top = getTopExp(ExtractExpression.MONTH_COMPOSER, arg);

        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getMonthFromLong()
    {
        Expression arg = new LiteralExpression(AkType.LONG, 20091230123045L);
        Expression top = getTopExp(ExtractExpression.DAY_COMPOSER, arg);

        assertEquals(30L, top.evaluation().eval().getInt());
    }

    @Test
    public void getMonthFromDouble()
    {
        Expression arg = new LiteralExpression(AkType.DOUBLE, 2345.5);
        Expression top = getTopExp(ExtractExpression.DAY_COMPOSER, arg);

        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getMonthFromYear()
    {
        Expression top = getTopExp(ExtractExpression.DATE_COMPOSER, getYear());
        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getMonthFromNull ()
    {
        Expression top = getTopExp(ExtractExpression.MONTH_COMPOSER, LiteralExpression.forNull());
        assertTrue (top.evaluation().eval().isNull());
    }
    // ----------------------- GET SECOND---------------------------------------
    @Test
    public void testBoundSecond ()
    {
        Expression arg = new LiteralExpression(AkType.LONG, 9999999L);
        Expression top = getTopExp(ExtractExpression.SECOND_COMPOSER, arg);

        assertTrue("Top should be null", top.evaluation().eval().isNull());
    }

    @Test
    public void getSecondFromDate()
    {
        Expression top = getTopExp(ExtractExpression.SECOND_COMPOSER, getDate());
        assertEquals(0L, top.evaluation().eval().getInt());
    }

    @Test
    public void getSecondFromDateTime()
    {
        Expression top = getTopExp(ExtractExpression.SECOND_COMPOSER, getDatetime());
        assertEquals(45L, top.evaluation().eval().getInt());
    }

    @Test
    public void getSecondFromTime()
    {
        Expression top = getTopExp(ExtractExpression.SECOND_COMPOSER, getTime());
        assertEquals(45L, top.evaluation().eval().getInt());
    }

    @Test
    public void getSecondFromTimestamp()
    {
        Expression top = getTopExp(ExtractExpression.SECOND_COMPOSER, getTimestamp());
        assertEquals(45L, top.evaluation().eval().getInt());
    }

    @Test
    public void getSecondFromVarchar()
    {
        Expression arg = new LiteralExpression(AkType.VARCHAR, "2009-12-30 12:30:10");
        Expression top = getTopExp(ExtractExpression.SECOND_COMPOSER, arg);

        assertEquals(10L, top.evaluation().eval().getInt());
    }

    @Test
    public void getSecondFromBadString()
    {
        Expression arg = new LiteralExpression(AkType.VARCHAR, "2009-30-30 12:30:10");
        Expression top = getTopExp(ExtractExpression.SECOND_COMPOSER, arg);

        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getSecondFromLong()
    {
        Expression arg = new LiteralExpression(AkType.LONG, 20091230123045L);
        Expression top = getTopExp(ExtractExpression.SECOND_COMPOSER, arg);

        assertEquals(45L, top.evaluation().eval().getInt());
    }

    @Test
    public void getSecondFromDouble()
    {
        Expression arg = new LiteralExpression(AkType.DOUBLE, 2345.3);
        Expression top = getTopExp(ExtractExpression.SECOND_COMPOSER, arg);

        assertEquals("SECOND(2345.3) ", 45, top.evaluation().eval().getInt());
    }

    @Test
    public void getSecondFromYear()
    {
        Expression top = getTopExp(ExtractExpression.SECOND_COMPOSER, getYear());
        assertEquals(0L, top.evaluation().eval().getInt());
    }


    @Test
    public void getSecondFromNull ()
    {
        Expression top = getTopExp(ExtractExpression.SECOND_COMPOSER, LiteralExpression.forNull());
        assertTrue (top.evaluation().eval().isNull());
    }
    // -------------------------GET TIME----------------------------------------
    @Test
    public void testBoundTime ()
    {
        Expression arg = new LiteralExpression(AkType.LONG, 9999L);
        Expression top = getTopExp(ExtractExpression.TIME_COMPOSER, arg);

        assertTrue("Top should be null", top.evaluation().eval().isNull());
    }

    @Test
    public void getTimeFromTime()
    {
        Expression top = getTopExp(ExtractExpression.TIME_COMPOSER, new LiteralExpression(AkType.TIME, 1234L));
        assertEquals(1234L, top.evaluation().eval().getTime());
    }

    @Test
    public void getTimeFromDate()
    {
        Expression top = getTopExp(ExtractExpression.TIME_COMPOSER, getDate());
        assertEquals(0L, top.evaluation().eval().getTime());
    }

    @Test
    public void getTimeFromDateTime()
    {
        Expression top = getTopExp(ExtractExpression.TIME_COMPOSER, getDatetime());
        assertEquals("11:30:45", Extractors.getLongExtractor(AkType.TIME).asString(top.evaluation().eval().getTime()));
    }

    @Test
    public void getTimeFromTimestamp()
    {
        Expression top = getTopExp(ExtractExpression.TIME_COMPOSER, getTimestamp());
        assertEquals("11:30:45", Extractors.getLongExtractor(AkType.TIME).asString(top.evaluation().eval().getTime()));
    }

    @Test
    public void getTimeFromVarchar()
    {
        Expression arg = new LiteralExpression(AkType.VARCHAR, "2009-12-30 12:30:10");
        Expression top = getTopExp(ExtractExpression.TIME_COMPOSER, arg);

        assertEquals("12:30:10", Extractors.getLongExtractor(AkType.TIME).asString(top.evaluation().eval().getTime()));
    }

    @Test
    public void getTimeFromBadString()
    {
        Expression arg = new LiteralExpression(AkType.VARCHAR, "2009-30-30 12:30:10");
        Expression top = getTopExp(ExtractExpression.TIME_COMPOSER, arg);

        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getTimeFromLong()
    {
        Expression arg = new LiteralExpression(AkType.LONG, 20091230123045L);
        Expression top = getTopExp(ExtractExpression.TIME_COMPOSER, arg);

        assertEquals("12:30:45", Extractors.getLongExtractor(AkType.TIME).asString(top.evaluation().eval().getTime()));

    }

    @Test
    public void getTimeFromDouble()
    {
        Expression arg = new LiteralExpression(AkType.DOUBLE, 2345.49);
        Expression top = getTopExp(ExtractExpression.TIME_COMPOSER, arg);

        assertEquals("TIME(2345.49) ", 2345, top.evaluation().eval().getTime());
    }

    @Test
    public void getTimeFromYear()
    {
        Expression top = getTopExp(ExtractExpression.TIME_COMPOSER, getYear());
        assertEquals(0L, top.evaluation().eval().getTime());
    }

    @Test
    public void getTimeFromNull ()
    {
        Expression top = getTopExp(ExtractExpression.TIME_COMPOSER, LiteralExpression.forNull());
        assertTrue (top.evaluation().eval().isNull());
    }
    // ----------------------- GET TIMESTAMP------------------------------------
    @Test
    public void getTimeStampBug () // bug 905525 - unit test passes
    {
        Expression timeStamp = new LiteralExpression(AkType.DATETIME,
                Extractors.getLongExtractor(AkType.DATETIME).getLong("1999-12-31 01:15:33"));
        Expression top = compose(ExtractExpression.TIMESTAMP_COMPOSER, Arrays.asList(timeStamp));

        String actual = Extractors.getLongExtractor(AkType.TIMESTAMP).asString(top.evaluation().eval().getTimestamp());
        assertEquals(actual, "1999-12-31 01:15:33");
    }

    @Test
    public void getTimestampFromTimestamp()
    {
        Expression top = getTopExp(ExtractExpression.TIMESTAMP_COMPOSER, new LiteralExpression(AkType.TIMESTAMP, 1234L));
        assertEquals(1234L, top.evaluation().eval().getTimestamp());
    }

    @Test
    public void getTimestampFromDate()
    {
        Expression top = getTopExp(ExtractExpression.TIMESTAMP_COMPOSER, getDate());
        assertEquals("2009-08-30 00:00:00", Extractors.getLongExtractor(AkType.TIMESTAMP).asString(top.evaluation().eval().getTimestamp()));
   }

    @Test
    public void getTimestampFromDateTime()
    {
        Expression top = getTopExp(ExtractExpression.TIMESTAMP_COMPOSER, getDatetime());
        assertEquals("2009-08-30 11:30:45", Extractors.getLongExtractor(AkType.TIMESTAMP).asString(top.evaluation().eval().getTimestamp()));
    }

    @Test
    public void getTimestampFromTime()
    {
        Expression top = getTopExp(ExtractExpression.TIMESTAMP_COMPOSER, getTime());
        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getTimestampFromVarchar()
    {
        Expression arg = new LiteralExpression(AkType.VARCHAR, "2009-12-30 12:30:10");
        Expression top = getTopExp(ExtractExpression.TIMESTAMP_COMPOSER, arg);

        assertEquals("2009-12-30 12:30:10", Extractors.getLongExtractor(AkType.TIMESTAMP).asString(top.evaluation().eval().getTimestamp()));
    }

    @Test
    public void getTimestampFromBadString()
    {
        Expression arg = new LiteralExpression(AkType.VARCHAR, "2009-30-30 12:30:10");
        Expression top = getTopExp(ExtractExpression.TIME_COMPOSER, arg);

        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getTimestampFromLong()
    {
        Expression arg = new LiteralExpression(AkType.LONG, 20091230123045L);
        Expression top = getTopExp(ExtractExpression.TIMESTAMP_COMPOSER, arg);

        assertEquals("2009-12-30 12:30:45", Extractors.getLongExtractor(AkType.TIMESTAMP).asString(top.evaluation().eval().getTimestamp()));

    }

    @Test
    public void getTimestampFromDouble()
    {
        Expression arg = new LiteralExpression(AkType.DOUBLE, 2345.5);
        Expression top = getTopExp(ExtractExpression.TIMESTAMP_COMPOSER, arg);

        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getTimestampFromYear()
    {
        Expression top = getTopExp(ExtractExpression.TIMESTAMP_COMPOSER, getYear());
        assertTrue(top.evaluation().eval().isNull());
    }


    @Test
    public void getTimestampFromNull ()
    {
        Expression top = getTopExp(ExtractExpression.TIMESTAMP_COMPOSER, LiteralExpression.forNull());
        assertTrue (top.evaluation().eval().isNull());
    }
    // ------------------------GET YEAR-----------------------------------------
    @Test
    public void getYearZero ()
    {
        Expression top = getTop("0000-01-00", ExtractExpression.YEAR_COMPOSER);
        assertEquals(0L, top.evaluation().eval().getInt());
    }

    @Test
    public void getYearFromTimestamp()
    {
        Expression top = getTopExp(ExtractExpression.YEAR_COMPOSER, getTimestamp());
        assertEquals(2009L, top.evaluation().eval().getInt());
    }

    @Test
    public void getYearFromDate()
    {
        Expression top = getTopExp(ExtractExpression.YEAR_COMPOSER, getDate());
        assertEquals(2009L, top.evaluation().eval().getInt());
   }

    @Test
    public void getYearstampFromDateTime()
    {
        Expression top = getTopExp(ExtractExpression.YEAR_COMPOSER, getDatetime());
        assertEquals(2009L, top.evaluation().eval().getInt());
    }

    @Test
    public void getYearFromTime()
    {
        Expression top = getTopExp(ExtractExpression.YEAR_COMPOSER, getTime());
        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getYearFromVarchar()
    {
        Expression arg = new LiteralExpression(AkType.VARCHAR, "2009-12-30 12:30:10");
        Expression top = getTopExp(ExtractExpression.YEAR_COMPOSER, arg);

        assertEquals(2009L, top.evaluation().eval().getInt());
    }

    @Test
    public void getYearFromBadString()
    {
        Expression arg = new LiteralExpression(AkType.VARCHAR, "2009-30-30 12:30:10");
        Expression top = getTopExp(ExtractExpression.YEAR_COMPOSER, arg);

        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getYearFromLong()
    {
        Expression arg = new LiteralExpression(AkType.LONG, 20091230123045L);
        Expression top = getTopExp(ExtractExpression.YEAR_COMPOSER, arg);

        assertEquals(2009L, top.evaluation().eval().getInt());

    }

    @Test
    public void getYearFromDouble()
    {
        Expression arg = new LiteralExpression(AkType.DOUBLE, 2345.5);
        Expression top = getTopExp(ExtractExpression.YEAR_COMPOSER, arg);

        assertTrue(top.evaluation().eval().isNull());
    }

    @Test
    public void getYearFromYear()
    {
        Expression top = getTopExp(ExtractExpression.YEAR_COMPOSER, getYear());
        assertEquals (2009, top.evaluation().eval().getInt());
    }

    @Test
    public void getYearFromNull ()
    {
        Expression top = getTopExp(ExtractExpression.YEAR_COMPOSER, LiteralExpression.forNull());
        assertTrue (top.evaluation().eval().isNull());
    }

    //--------------------------------------------------------------------------

    @Override
    protected CompositionTestInfo getTestInfo()
    {
        return testInfo;
    }

    @Override
    protected ExpressionComposer getComposer()
    {
        return ExtractExpression.DATE_COMPOSER;
    }

    private Expression getDatetime()
    {
        return new LiteralExpression(AkType.DATETIME, 20090830113045L);
    }

    private Expression getDate()
    {
        return new LiteralExpression(AkType.DATE, Extractors.getLongExtractor(AkType.DATE).getLong("2009-08-30"));
    }

    private Expression getTime()
    {
        return new LiteralExpression(AkType.TIME, Extractors.getLongExtractor(AkType.TIME).getLong("11:30:45"));
    }

    private Expression getTimestamp()
    {
        return new LiteralExpression(AkType.TIMESTAMP,
                Extractors.getLongExtractor(AkType.TIMESTAMP).getLong("2009-08-30 11:30:45"));
    }

    private Expression getYear()
    {
        return new LiteralExpression(AkType.YEAR, 109L);
    }

    private Expression getTopExp(ExpressionComposer composer, Expression arg)
    {
        return compose(composer, Arrays.asList(arg));
    }

    private Expression getTop (String input, ExpressionComposer comp)
    {
        Expression date = new LiteralExpression(AkType.DATE,
                Extractors.getLongExtractor(AkType.DATE).getLong(input));
        Expression top = compose(comp, Arrays.asList(date));
        return top;
    }

    private static Expression getExp (AkType type, String input)
    {
        return new LiteralExpression(type, Extractors.getLongExtractor(type).getLong(input));
    }

    private void testDayOfYear(String input, AkType inputType, Long output)
    {
        ValueSource top  = getTopExp(ExtractExpression.DAY_YEAR_COMPOSER,
                                getExp(inputType,input)).evaluation().eval();
        if (output == null)
            assertTrue("DayOfYear(" + input + "). Top should be null", top.isNull());
        else
            assertEquals ("DayOfYear(" + input + ")", output.longValue(), top.getInt());
    }

    @Override
    public boolean alreadyExc()
    {
        return false;
    }
}
