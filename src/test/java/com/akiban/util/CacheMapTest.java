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
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertSame;
import static junit.framework.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public final class CacheMapTest {

    @SuppressWarnings("unused")
    private static class HookedCacheMap<K,V> extends CacheMap<K,V>{
        private int allocations = 0;

        private HookedCacheMap() {
        }

        private HookedCacheMap(Allocator<K, V> kvAllocator) {
            super(kvAllocator);
        }

        private HookedCacheMap(int size) {
            super(size);
        }

        private HookedCacheMap(int size, Allocator<K, V> kvAllocator) {
            super(size, kvAllocator);
        }

        @Override
        protected void allocatorHook() {
            ++allocations;
        }
    }

    private static class TestAllocator implements CacheMap.Allocator<java.lang.Integer, java.lang.String> {
        @Override
        public String allocateFor(Integer key) {
            return new String(String.format("allocated key %d", key));
        }
    }

    @Test
    public void withAllocator() {
        HookedCacheMap<Integer,String> map = new HookedCacheMap<>(new TestAllocator());

        final String result = map.get(1);
        assertEquals("map[1]", "allocated key 1", result);
        assertEquals("allocations", 1, map.allocations);
        assertSame("second call", result, map.get(1));
        assertEquals("allocations", 1, map.allocations);
        assertSame("removing", result, map.remove(1));

        final String result2 = map.get(1);
        assertEquals("map[1]", "allocated key 1", result2);
        if (result == result2) {
            fail("Expected new string");
        }
        assertEquals("allocations", 2, map.allocations);
    }

    @Test
    public void testEquality() {
        CacheMap<Integer,String> cacheMap = new CacheMap<>();
        HashMap<Integer,String> expectedMap = new HashMap<>();

        cacheMap.put(1, "one");
        expectedMap.put(1, "one");
        cacheMap.put(2, "two");
        expectedMap.put(2, "two");

        testEquality(true, expectedMap, cacheMap);
        testEquality(true, cacheMap, expectedMap);

        expectedMap.put(3, "three");
        testEquality(false, expectedMap, cacheMap);
        testEquality(false, cacheMap, expectedMap);
    }

    private static void testEquality(boolean shouldBeEqual, Map<?,?> map1, Map<?,?> map2) {
        if (shouldBeEqual != map1.equals(map2)) {
            fail(String.format("%s equals %s expected %s", map1, map2, shouldBeEqual));
        }
        int map1Hash = map1.hashCode();
        int map2Hash = map2.hashCode();
        if (shouldBeEqual != (map1Hash == map2Hash)) {
            fail(String.format("%d ?= %d expected %s", map1Hash, map2Hash, shouldBeEqual));
        }
    }

    @Test
    public void lru() {
        HookedCacheMap<Integer,String> map = new HookedCacheMap<>(1, new TestAllocator());

        assertEquals("size", 0, map.size());

        assertNull("expected null", map.put(1, "explicit key a"));
        assertEquals("size", 1, map.size());
        
        assertEquals("old value", "explicit key a", map.put(1, "explicit key b"));
        assertEquals("size", 1, map.size());
        assertEquals("allocations", 0, map.allocations);

        assertEquals("generated value", "allocated key 2", map.get(2));
        assertEquals("size", 1, map.size());
        assertEquals("allocations", 1, map.allocations);

        Map<Integer,String> expectedMap = new HashMap<>();
        expectedMap.put(2, "allocated key 2");
        assertEquals("size", 1, map.size());
        assertEquals("map values", expectedMap, map);
        assertEquals("allocations", 1, map.allocations);
    }

    @Test
    public void nullAllocator() {
        CacheMap<Integer,String> map = new CacheMap<>(null);
        assertNull("expected null", map.get(1));
    }

    @Test
    public void nullKey() {
        CacheMap<Integer,String> map = new CacheMap<>(null);
        map.put(null, "hello");
        assertEquals("get(null)", "hello", map.get(null));
    }

    @Test
    public void nullValue() {
        CacheMap<Integer,String> map = new CacheMap<>(null);
        map.put(1, null);
        assertEquals("null value", null, map.get(1));
    }

    @Test(expected=IllegalArgumentException.class)
    public void sizeIsZero() {
        new CacheMap<Integer, String>(0);
    }

    @Test(expected=IllegalArgumentException.class)
    public void sizeIsNegative() {
        new CacheMap<Integer, String>(-10);
    }

    @Test(expected=ClassCastException.class)
    public void allocatorCastException() {
        @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
        CacheMap<Integer,String> map = new CacheMap<>(new TestAllocator());
        map.get("1");
    }
}
