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

package com.akiban.ais.gwtutilstests;

import java.util.EnumSet;
import java.util.Set;

import junit.framework.Assert;

import org.junit.Test;

import com.akiban.ais.gwtutils.SerializableEnumSet;

public class SerializableEnumSetTest
{
    private enum TestEnum
    {
        T01, T02, T03, T04, T05, T06, T07, T08, T09, T10,
        T11, T12, T13, T14, T15, T16, T17, T18, T19, T20,
        T21, T22, T23, T24, T25, T26, T27, T28, T29, T30,
        T31
    }

    private enum TooBig
    {
        T01, T02, T03, T04, T05, T06, T07, T08, T09, T10,
        T11, T12, T13, T14, T15, T16, T17, T18, T19, T20,
        T21, T22, T23, T24, T25, T26, T27, T28, T29, T30,
        T31, T32
    }

    private int lastSerialized = -1;
    private <T extends Enum<T>> Set<T> copyViaSerialization(Class<T> cls, EnumSet<T> set)
    {
        {
            com.akiban.ais.gwtutils.SerializableEnumSet<T> tmpOne = new com.akiban.ais.gwtutils.SerializableEnumSet(cls);
            tmpOne.addAll(set);
            lastSerialized = tmpOne.toInt();
        }

        com.akiban.ais.gwtutils.SerializableEnumSet<T> tmpTwo = new com.akiban.ais.gwtutils.SerializableEnumSet(cls);
        tmpTwo.loadInt(lastSerialized);
        return tmpTwo;
    }

    @Test
    public void testEmptySet()
    {
        EnumSet<TestEnum> expected = EnumSet.noneOf(TestEnum.class);
        Set<TestEnum> actual = copyViaSerialization(TestEnum.class, expected);

        Assert.assertEquals("s="+lastSerialized, expected, actual);
    }

    @Test
    public void testFullSet()
    {
        EnumSet<TestEnum> expected = EnumSet.allOf(TestEnum.class);
        Set<TestEnum> actual = copyViaSerialization(TestEnum.class, expected);

        Assert.assertEquals("s="+lastSerialized, expected, actual);
    }

    @Test
    public void testOneSet()
    {
        // once for first item
        {
            EnumSet<TestEnum> expected = EnumSet.of(TestEnum.T01);
            Set<TestEnum> actual = copyViaSerialization(TestEnum.class, expected);

            Assert.assertEquals("s="+lastSerialized, expected, actual);
        }
        // once for last item
        {
            EnumSet<TestEnum> expected = EnumSet.of(TestEnum.T31);
            Set<TestEnum> actual = copyViaSerialization(TestEnum.class, expected);

            Assert.assertEquals("s="+lastSerialized, expected, actual);
        }
        // once for middle item
        {
            EnumSet<TestEnum> expected = EnumSet.of(TestEnum.T27);
            Set<TestEnum> actual = copyViaSerialization(TestEnum.class, expected);

            Assert.assertEquals("s="+lastSerialized, expected, actual);
        }
    }

    @Test
    public void testPartialSet()
    {
        // once when we're removing the last item
        {
            EnumSet<TestEnum> expected = EnumSet.allOf(TestEnum.class);
            expected.remove(TestEnum.T31);
            Set<TestEnum> actual = copyViaSerialization(TestEnum.class, expected);

            Assert.assertEquals("s="+lastSerialized, expected, actual);
        }
        // once when we're removing the first item
        {
            EnumSet<TestEnum> expected = EnumSet.allOf(TestEnum.class);
            expected.remove(TestEnum.T01);
            Set<TestEnum> actual = copyViaSerialization(TestEnum.class, expected);

            Assert.assertEquals("s="+lastSerialized, expected, actual);
        }
        // once where we're removing a few from the middle
        {
            EnumSet<TestEnum> expected = EnumSet.allOf(TestEnum.class);
            expected.remove(TestEnum.T09);
            expected.remove(TestEnum.T11);
            expected.remove(TestEnum.T27);
            Set<TestEnum> actual = copyViaSerialization(TestEnum.class, expected);

            Assert.assertEquals("s="+lastSerialized, expected, actual);
        }
    }

    @Test
    public void testToIntAfterLoadInt()
    {
        SerializableEnumSet<TestEnum> x = new SerializableEnumSet<>(TestEnum.class);
        x.loadInt(0);
        Assert.assertEquals(0, x.toInt());
    }

    @Test(expected=NullPointerException.class)
    public void testNullSet()
    {
        copyViaSerialization(TestEnum.class, null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testTooBig()
    {
        new com.akiban.ais.gwtutils.SerializableEnumSet(TooBig.class);
    }
}
