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

package com.akiban.util;

import static junit.framework.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;

public final class ClearingIterableTest
{
    @Test
    public void mainTest()
    {
        List<Integer> list = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));

        Iterator<Integer> iter = ClearingIterable.from(list).iterator();

        assertEquals(iter.next().intValue(), 1);
        assertArray(list, 1, 2, 3, 4, 5);

        {
            int count = 0, sum = 0;
            for (Integer i : list)
            {
                ++count;
                sum += i;
            }
            assertEquals(count, 5);
            assertEquals(sum, 1 + 2 + 3 + 4 + 5);
            assertArray(list, 1, 2, 3, 4, 5);
        }

        while(iter.hasNext())
        {
            iter.next();
        }

        assertArray(list);
    }

    @Test
    public void removeTest()
    {
        List<Integer> list = new ArrayList<>(Arrays.asList(1, 2, 3));
        Iterator<Integer> iter = ClearingIterable.from(list).iterator();

        assertEquals(iter.next().intValue(), 1);
        assertArray(list, 1, 2, 3);
        iter.remove();
        assertArray(list, 2, 3);

        assertEquals(iter.next().intValue(), 2);
        assertArray(list, 2, 3);
        iter.remove();
        assertArray(list, 3);

        assertEquals(iter.next().intValue(), 3);
        assertArray(list);
        iter.remove();
        assertArray(list);
    }

    @Test
    public void foreachTest()
    {
        List<Integer> list = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));

        {
            int count=0, sum = 0;
            for(Integer i : list)
            {
                ++count;
                sum += i;
            }
            assertEquals(count, 5);
            assertEquals(sum, 1 + 2 + 3 + 4 + 5);
            assertArray(list, 1, 2, 3, 4, 5);
        }

        {
            int count=0, sum = 0;
            for(Integer i : ClearingIterable.from(list))
            {
                ++count;
                sum += i;
            }
            assertEquals(count, 5);
            assertEquals(sum, 1 + 2 + 3 + 4 + 5);
            assertArray(list);
        }
    }

    private static void assertArray(List<Integer> list, int... expected)
    {
        assertEquals("list size", expected.length, list.size());
        for (int i=0; i<expected.length; ++i)
        {
            assertEquals("at index " + expected, expected[i], list.get(i).intValue());
        }
    }
}
