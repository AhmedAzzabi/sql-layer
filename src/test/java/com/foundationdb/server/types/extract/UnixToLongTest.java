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

package com.foundationdb.server.types.extract;

import java.util.TimeZone;
import com.foundationdb.server.types.AkType;
import org.junit.Test;
import java.util.Calendar;

import static org.junit.Assert.assertEquals;

public class UnixToLongTest
{
    private final TimeZone defaultTimeZone = TimeZone.getDefault();
    private final String testTimeZone = "UTC";

    @Test
    public void testDate()
    {
        ConverterTestUtils.setGlobalTimezone(testTimeZone);
        long unix = Extractors.getLongExtractor(AkType.DATE).stdLongToUnix(1008673L);
        assertEquals(0, unix);

        long stdLong = Extractors.getLongExtractor(AkType.DATE).unixToStdLong(0);
        assertEquals(1008673L, stdLong);
    }

    @Test
    public void testDateTime()
    {
        ConverterTestUtils.setGlobalTimezone(testTimeZone);
        long unix = Extractors.getLongExtractor(AkType.DATETIME).stdLongToUnix(20061107123010L);
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(testTimeZone));
        calendar.set(Calendar.YEAR, 2006);
        calendar.set(Calendar.MONTH, 10);
        calendar.set(Calendar.DAY_OF_MONTH, 7);
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        calendar.set(Calendar.MINUTE, 30);
        calendar.set(Calendar.SECOND, 10);
        calendar.set(Calendar.MILLISECOND, 0);
        assertEquals((long)calendar.getTimeInMillis(), unix);

        long stdDate = Extractors.getLongExtractor(AkType.DATETIME).unixToStdLong(unix);
        assertEquals(20061107123010L, stdDate);     
    }

    @Test
    public void testTime()
    {
        ConverterTestUtils.setGlobalTimezone(testTimeZone);
        long stdLong = 123010L;
        long unix = Extractors.getLongExtractor(AkType.TIME).stdLongToUnix(stdLong);

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(testTimeZone));
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        calendar.set(Calendar.MINUTE, 30);
        calendar.set(Calendar.SECOND, 10);
        calendar.set(Calendar.MILLISECOND, 0);

        long stdLong1 = Extractors.getLongExtractor(AkType.TIME).unixToStdLong(unix);
        long stdLong2 = Extractors.getLongExtractor(AkType.TIME).unixToStdLong(calendar.getTimeInMillis());

        assertEquals(stdLong, stdLong1);
        assertEquals(stdLong, stdLong2);
    }

    @Test
    public void testYear()
    {
        ConverterTestUtils.setGlobalTimezone(testTimeZone);
        int year = 1991;
        long unix = Extractors.getLongExtractor(AkType.YEAR).stdLongToUnix(year);

        long stdLong1 = Extractors.getLongExtractor(AkType.YEAR).unixToStdLong(unix);
        assertEquals(year, stdLong1);
     
        ConverterTestUtils.setGlobalTimezone(defaultTimeZone.getID());
    }
}
