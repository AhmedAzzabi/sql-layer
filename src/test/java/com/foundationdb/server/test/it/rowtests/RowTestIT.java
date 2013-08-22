/**
 * Copyright (C) 2009-2013 FoundationDB, LLC
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

package com.foundationdb.server.test.it.rowtests;

import com.foundationdb.server.api.dml.scan.NewRow;
import com.foundationdb.server.rowdata.RowData;
import com.foundationdb.server.rowdata.RowDef;
import com.foundationdb.server.api.dml.scan.LegacyRowWrapper;
import com.foundationdb.server.api.dml.scan.NiceRow;
import com.foundationdb.server.error.InvalidOperationException;
import com.foundationdb.server.test.it.ITBase;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

public class RowTestIT extends ITBase
{
    @Test
    public void rowConversionTestNoNulls() throws InvalidOperationException
    {
        int t = createTable("s",
                                "t",
                                "id int not null primary key",
                                "a int not null",
                                "b int not null");
        NewRow original = createNewRow(t);
        int cId = 0;
        int cA = 1;
        int cB = 2;
        // Insert longs, not integers, because Persistit stores all ints as 8-byte.
        original.put(cId, 100L);
        original.put(cA, 200L);
        original.put(cB, 300L);
        RowDef rowDef = getRowDef(t);
        RowData rowData = original.toRowData();
        NiceRow reconstituted = (NiceRow) NiceRow.fromRowData(rowData, rowDef);
        assertEquals(original, reconstituted);
    }

    @Test
    public void rowConversionTestWithNulls() throws InvalidOperationException
    {
        int t = createTable("s",
                                "t",
                                "id int not null primary key",
                                "a int not null",
                                "b int");
        NewRow original = createNewRow(t);
        int cId = 0;
        int cA = 1;
        int cB = 2;
        // Insert longs, not integers, because Persistit stores all ints as 8-byte.
        original.put(cId, 100L);
        original.put(cA, 200L);
        original.put(cB, null);
        RowDef rowDef = getRowDef(t);
        RowData rowData = original.toRowData();
        NiceRow reconstituted = (NiceRow) NiceRow.fromRowData(rowData, rowDef);
        assertEquals(original, reconstituted);
    }

    @Test
    public void niceRowUpdate() throws InvalidOperationException
    {
        int t = createTable("s",
                                "t",
                                "id int not null primary key",
                                "a int",
                                "b int",
                                "c int");
        NewRow row = createNewRow(t);
        int cId = 0;
        int cA = 1;
        int cB = 2;
        int cC = 3;
        // Insert longs, not integers, because Persistit stores all ints as 8-byte.
        row.put(cId, 100L);
        row.put(cA, 200L);
        row.put(cB, 300L);
        row.put(cC, null);
        assertEquals(100L, row.get(cId));
        assertEquals(200L, row.get(cA));
        assertEquals(300L, row.get(cB));
        assertNull(row.get(cC));
        row.put(cA, 222L);
        row.put(cB, null);
        row.put(cC, 444L);
        assertEquals(100L, row.get(cId));
        assertEquals(222L, row.get(cA));
        assertNull(row.get(cB));
        assertEquals(444L, row.get(cC));
    }

    @Test
    public void rowDataUpdate() throws InvalidOperationException
    {
        int t = createTable("s",
                                "t",
                                "id int not null primary key",
                                "a int",
                                "b int",
                                "c int");
        NewRow niceRow = createNewRow(t);
        int cId = 0;
        int cA = 1;
        int cB = 2;
        int cC = 3;
        // Insert longs, not integers, because Persistit stores all ints as 8-byte.
        niceRow.put(cId, 100L);
        niceRow.put(cA, 200L);
        niceRow.put(cB, 300L);
        niceRow.put(cC, null);
        LegacyRowWrapper legacyRow = new LegacyRowWrapper(niceRow.getRowDef(), niceRow.toRowData());
        assertEquals(100L, legacyRow.get(cId));
        assertEquals(200L, legacyRow.get(cA));
        assertEquals(300L, legacyRow.get(cB));
        assertNull(legacyRow.get(cC));
        legacyRow.put(cA, 222L);
        legacyRow.put(cB, null);
        legacyRow.put(cC, 444L);
        assertEquals(100L, legacyRow.get(cId));
        assertEquals(222L, legacyRow.get(cA));
        assertNull(legacyRow.get(cB));
        assertEquals(444L, legacyRow.get(cC));
    }

    @Test
    public void legacyRowConversion() throws InvalidOperationException
    {
        // LegacyRowWrapper converts to NiceRow on update, back to RowData on toRowData().
        // Check the conversions.
        int t = createTable("s",
                                "t",
                                "id int not null primary key",
                                "a int",
                                "b int",
                                "c int");
        NewRow niceRow = createNewRow(t);
        int cId = 0;
        int cA = 1;
        int cB = 2;
        int cC = 3;
        // Insert longs, not integers, because Persistit stores all ints as 8-byte.
        niceRow.put(cId, 0);
        niceRow.put(cA, 0L);
        niceRow.put(cB, 0L);
        niceRow.put(cC, 0L);
        // Create initial legacy row
        LegacyRowWrapper legacyRow = new LegacyRowWrapper(niceRow.getRowDef(), niceRow.toRowData());
        assertEquals(0L, legacyRow.get(cA));
        assertEquals(0L, legacyRow.get(cB));
        assertEquals(0L, legacyRow.get(cC));
        // Apply a few updates
        legacyRow.put(cA, 1L);
        legacyRow.put(cB, 1L);
        legacyRow.put(cC, 1L);
        // Check the updates (should be a NiceRow)
        assertEquals(1L, legacyRow.get(cA));
        assertEquals(1L, legacyRow.get(cB));
        assertEquals(1L, legacyRow.get(cC));
        // Convert to LegacyRow and check NiceRow created from the legacy row's RowData
        RowDef rowDef = getRowDef(t);
        niceRow = (NiceRow) NiceRow.fromRowData(legacyRow.toRowData(), rowDef);
        assertEquals(1L, niceRow.get(cA));
        assertEquals(1L, niceRow.get(cB));
        assertEquals(1L, niceRow.get(cC));
        // Convert back to NiceRow and check state again
        legacyRow.put(cA, 2L);
        legacyRow.put(cB, 2L);
        legacyRow.put(cC, 2L);
        assertEquals(2L, legacyRow.get(cA));
        assertEquals(2L, legacyRow.get(cB));
        assertEquals(2L, legacyRow.get(cC));
    }
}
