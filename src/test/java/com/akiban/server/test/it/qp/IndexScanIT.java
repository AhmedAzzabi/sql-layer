/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.server.test.it.qp;

import com.akiban.qp.expression.IndexBound;
import com.akiban.qp.expression.IndexKeyRange;
import com.akiban.qp.operator.Cursor;
import com.akiban.qp.operator.Operator;
import com.akiban.server.api.dml.SetColumnSelector;
import org.junit.Before;
import org.junit.Test;

import static com.akiban.qp.operator.API.cursor;
import static com.akiban.qp.operator.API.indexScan_Default;

public class IndexScanIT extends OperatorITBase
{
    @Before
    public void before()
    {
        super.before();
        use(db);
    }

    @Test
    public void testExactMatchAbsent()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, false, indexKeyRange(299, true, 299, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testExactMatchPresent()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, false, indexKeyRange(212, true, 212, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(2, 21, 212)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testEmptyRange()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, false, indexKeyRange(218, true, 219, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{};
        compareRenderedHKeys(expected, cursor);
    }

    // Naming schema for next tests:
    // testLoABHiCD
    // A: Inclusive/Exclusive for lo bound
    // B: Match/Miss indicates whether the lo bound matches or misses an actual value in the db
    // C: Inclusive/Exclusive for hi bound
    // D: Match/Miss indicates whether the hi bound matches or misses an actual value in the db

    @Test
    public void testLoInclusiveMatchHiInclusiveMatch()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, false, indexKeyRange(121, true, 211, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(1, 12, 121), hkey(1, 12, 122), hkey(2, 21, 211)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoInclusiveMatchHiInclusiveMiss()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, false, indexKeyRange(212, true, 223, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(2, 21, 212), hkey(2, 22, 221), hkey(2, 22, 222)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoInclusiveMatchHiExclusiveMatch()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, false, indexKeyRange(212, true, 222, false));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(2, 21, 212), hkey(2, 22, 221)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoInclusiveMatchHiExclusiveMiss()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, false, indexKeyRange(212, true, 223, false));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(2, 21, 212), hkey(2, 22, 221), hkey(2, 22, 222)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoInclusiveMissHiInclusiveMatch()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, false, indexKeyRange(100, true, 121, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(1, 11, 111), hkey(1, 11, 112), hkey(1, 12, 121)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoInclusiveMissHiInclusiveMiss()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, false, indexKeyRange(100, true, 125, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(1, 11, 111), hkey(1, 11, 112), hkey(1, 12, 121), hkey(1, 12, 122)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoInclusiveMissHiExclusiveMatch()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, false, indexKeyRange(100, true, 122, false));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(1, 11, 111), hkey(1, 11, 112), hkey(1, 12, 121)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoInclusiveMissHiExclusiveMiss()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, false, indexKeyRange(100, true, 125, false));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(1, 11, 111), hkey(1, 11, 112), hkey(1, 12, 121), hkey(1, 12, 122)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoExclusiveMatchHiInclusiveMatch()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, false, indexKeyRange(121, false, 211, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(1, 12, 122), hkey(2, 21, 211)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoExclusiveMatchHiInclusiveMiss()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, false, indexKeyRange(212, false, 223, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(2, 22, 221), hkey(2, 22, 222)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoExclusiveMatchHiExclusiveMatch()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, false, indexKeyRange(212, false, 222, false));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(2, 22, 221)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoExclusiveMatchHiExclusiveMiss()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, false, indexKeyRange(212, false, 223, false));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(2, 22, 221), hkey(2, 22, 222)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoExclusiveMissHiInclusiveMatch()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, false, indexKeyRange(100, false, 121, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(1, 11, 111), hkey(1, 11, 112), hkey(1, 12, 121)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoExclusiveMissHiInclusiveMiss()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, false, indexKeyRange(100, false, 125, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(1, 11, 111), hkey(1, 11, 112), hkey(1, 12, 121), hkey(1, 12, 122)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoExclusiveMissHiExclusiveMatch()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, false, indexKeyRange(100, false, 122, false));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(1, 11, 111), hkey(1, 11, 112), hkey(1, 12, 121)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoExclusiveMissHiExclusiveMiss()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, false, indexKeyRange(100, false, 125, false));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(1, 11, 111), hkey(1, 11, 112), hkey(1, 12, 121), hkey(1, 12, 122)};
        compareRenderedHKeys(expected, cursor);
    }

    // Reverse versions of above tests

    @Test
    public void testExactMatchAbsentReverse()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, true, indexKeyRange(299, true, 299, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testExactMatchPresentReverse()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, true, indexKeyRange(212, true, 212, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(2, 21, 212)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testEmptyRangeReverse()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, true, indexKeyRange(218, true, 219, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoInclusiveMatchHiInclusiveMatchReverse()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, true, indexKeyRange(121, true, 211, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(2, 21, 211), hkey(1, 12, 122), hkey(1, 12, 121)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoInclusiveMatchHiInclusiveMissReverse()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, true, indexKeyRange(212, true, 223, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(2, 22, 222), hkey(2, 22, 221), hkey(2, 21, 212)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoInclusiveMatchHiExclusiveMatchReverse()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, true, indexKeyRange(212, true, 222, false));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(2, 22, 221), hkey(2, 21, 212)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoInclusiveMatchHiExclusiveMissReverse()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, true, indexKeyRange(212, true, 223, false));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(2, 22, 222), hkey(2, 22, 221), hkey(2, 21, 212)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoInclusiveMissHiInclusiveMatchReverse()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, true, indexKeyRange(100, true, 121, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(1, 12, 121), hkey(1, 11, 112), hkey(1, 11, 111)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoInclusiveMissHiInclusiveMissReverse()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, true, indexKeyRange(100, true, 125, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(1, 12, 122), hkey(1, 12, 121), hkey(1, 11, 112), hkey(1, 11, 111)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoInclusiveMissHiExclusiveMatchReverse()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, true, indexKeyRange(100, true, 122, false));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(1, 12, 121), hkey(1, 11, 112), hkey(1, 11, 111)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoInclusiveMissHiExclusiveMissReverse()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, true, indexKeyRange(100, true, 125, false));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(1, 12, 122), hkey(1, 12, 121), hkey(1, 11, 112), hkey(1, 11, 111)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoExclusiveMatchHiInclusiveMatchReverse()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, true, indexKeyRange(121, false, 211, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(2, 21, 211), hkey(1, 12, 122)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoExclusiveMatchHiInclusiveMissReverse()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, true, indexKeyRange(212, false, 223, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(2, 22, 222), hkey(2, 22, 221)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoExclusiveMatchHiExclusiveMatchReverse()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, true, indexKeyRange(212, false, 222, false));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(2, 22, 221)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoExclusiveMatchHiExclusiveMissReverse()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, true, indexKeyRange(212, false, 223, false));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(2, 22, 222), hkey(2, 22, 221)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoExclusiveMissHiInclusiveMatchReverse()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, true, indexKeyRange(100, false, 121, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(1, 12, 121), hkey(1, 11, 112), hkey(1, 11, 111)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoExclusiveMissHiInclusiveMissReverse()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, true, indexKeyRange(100, false, 125, true));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(1, 12, 122), hkey(1, 12, 121), hkey(1, 11, 112), hkey(1, 11, 111)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoExclusiveMissHiExclusiveMatchReverse()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, true, indexKeyRange(100, false, 122, false));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(1, 12, 121), hkey(1, 11, 112), hkey(1, 11, 111)};
        compareRenderedHKeys(expected, cursor);
    }

    @Test
    public void testLoExclusiveMissHiExclusiveMissReverse()
    {
        Operator indexScan = indexScan_Default(itemIidIndexRowType, true, indexKeyRange(100, false, 125, false));
        Cursor cursor = cursor(indexScan, adapter);
        String[] expected = new String[]{hkey(1, 12, 122), hkey(1, 12, 121), hkey(1, 11, 112), hkey(1, 11, 111)};
        compareRenderedHKeys(expected, cursor);
    }

    // For use by this class

    private IndexKeyRange indexKeyRange(int lo, boolean loInclusive, int hi, boolean hiInclusive)
    {
        return new IndexKeyRange(bound(lo), loInclusive, bound(hi), hiInclusive);
    }

    private IndexBound bound(int iid)
    {
        return new IndexBound(row(itemIidIndexRowType, iid), new SetColumnSelector(0));
    }

    private String hkey(int cid, int oid, int iid)
    {
        return String.format("{1,(long)%s,2,(long)%s,3,(long)%s}", cid, oid, iid);
    }
}
