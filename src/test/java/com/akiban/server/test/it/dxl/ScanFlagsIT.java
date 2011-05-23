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

package com.akiban.server.test.it.dxl;

import com.akiban.ais.model.Index;
import com.akiban.ais.model.UserTable;
import com.akiban.server.InvalidOperationException;
import com.akiban.server.RowData;
import com.akiban.server.RowDef;
import com.akiban.server.api.dml.scan.CursorId;
import com.akiban.server.api.dml.scan.LegacyScanRequest;
import com.akiban.server.api.dml.scan.NewRow;
import com.akiban.server.api.dml.scan.ScanLimit;
import com.akiban.server.test.it.ITBase;
import org.junit.Test;

import java.util.Iterator;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.assertNotNull;

public class ScanFlagsIT extends ITBase
{
/*
    // Useful in profiling
    @Test
    public void testSameQueryManyTimes() throws InvalidOperationException
    {
        rowDefId = createTable("schema", "t", "id int key");
        for (int x : new int[]{1, 2, 3, 4, 5}) {
            dml().writeRow(session(), createNewRow(rowDefId, x));
        }
        final int N = 10000000;
        for (int i = 0; i < N; i++) {
            LegacyScanRequest request = new LegacyScanRequest(rowDefId,
                                                              bound(2),
                                                              null,
                                                              bound(2),
                                                              null,
                                                              new byte[]{1},
                                                              0, // index id, 0 = table scan
                                                              DEFAULT,  // scan flags
                                                              ScanLimit.NONE);
            ListRowOutput output = new ListRowOutput();
            CursorId cursorId = dml().openCursor(session(), aisGeneration(), request);
            boolean more = dml().scanSome(session(), cursorId, output);
            assertTrue(!more);
        }
    }
*/

    @Test
    public void testFullScanAsc() throws InvalidOperationException
    {
        List<NewRow> actual = query(DEFAULT, null, null,
                                    0, 1, 2, 3, 4);
        checkOutput(actual, 0, 1, 2, 3, 4);
    }

    @Test
    public void testFullScanDesc() throws InvalidOperationException
    {
        List<NewRow> actual = query(DESCENDING, null, null,
                                    0, 1, 2, 3, 4);
        checkOutput(actual, 4, 3, 2, 1, 0);
    }

    @Test
    public void testGTAsc() throws InvalidOperationException
    {
        List<NewRow> actual = query(START_EXCLUSIVE, 2, null,
                                    0, 1, 2, 3, 4);
        checkOutput(actual, 3, 4);
    }

    @Test
    public void testGTDesc() throws InvalidOperationException
    {
        List<NewRow> actual = query(DESCENDING | START_EXCLUSIVE, 2, null, 0, 1, 2, 3, 4);
        checkOutput(actual, 4, 3);
    }

    @Test
    public void testGEAsc() throws InvalidOperationException
    {
        List<NewRow> actual = query(DEFAULT, 2, null,
                                    0, 1, 2, 3, 4);
        checkOutput(actual, 2, 3, 4);
    }

    @Test
    public void testGEDesc() throws InvalidOperationException
    {
        List<NewRow> actual = query(DESCENDING, 2, null,
                                    0, 1, 2, 3, 4);
        checkOutput(actual, 4, 3, 2);
    }

    @Test
    public void testLTAsc() throws InvalidOperationException
    {
        List<NewRow> actual = query(END_EXCLUSIVE, null, 2,
                                    0, 1, 2, 3, 4);
        checkOutput(actual, 0, 1);
    }

    @Test
    public void testLTDesc() throws InvalidOperationException
    {
        List<NewRow> actual = query(DESCENDING | END_EXCLUSIVE, null, 2,
                                    0, 1, 2, 3, 4);
        checkOutput(actual, 1, 0);
    }

    @Test
    public void testLEAsc() throws InvalidOperationException
    {
        List<NewRow> actual = query(DEFAULT, null, 2,
                                    0, 1, 2, 3, 4);
        checkOutput(actual, 0, 1, 2);
    }

    @Test
    public void testLEDesc() throws InvalidOperationException
    {
        List<NewRow> actual = query(DESCENDING, null, 2,
                                    0, 1, 2, 3, 4);
        checkOutput(actual, 2, 1, 0);
    }

    private List<NewRow> query(int flags, Integer start, Integer end, int ... values) throws InvalidOperationException
    {
        rowDefId = createTable("schema", "t", "id int key, idcopy int, key(idcopy)");
        UserTable table = super.getUserTable(rowDefId);
        Index idCopyIndex = null;
        for (Index index : table.getIndexes()) {
            if (!index.isPrimaryKey()) {
                idCopyIndex = index;
            }
        }
        assertNotNull(idCopyIndex);
        for (int x : values) {
            dml().writeRow(session(), createNewRow(rowDefId, x, x));
        }
        LegacyScanRequest request = new LegacyScanRequest(rowDefId,
                                                          bound(start),
                                                          null,
                                                          bound(end),
                                                          null,
                                                          new byte[]{1},
                                                          idCopyIndex.getIndexId(),
                                                          flags,  // scan flags
                                                          ScanLimit.NONE);
        ListRowOutput output = new ListRowOutput();
        CursorId cursorId = dml().openCursor(session(), aisGeneration(), request);
        dml().scanSome(session(), cursorId, output);
        dml().closeCursor(session(), cursorId);
        return output.getRows();
    }

    private void checkOutput(List<NewRow> actual, int... expected)
    {
        assertEquals(expected.length, actual.size());
        Iterator<NewRow> a = actual.iterator();
        for (int e : expected) {
            assertEquals((long) e, ((Long)a.next().get(0)).longValue());
        }
    }

    private RowData bound(Integer x)
    {
        RowData rowData = null;
        if (x != null) {
            rowData = new RowData(new byte[100]);
            RowDef rowDef = rowDefCache().rowDef(rowDefId);
            rowData.createRow(rowDef, new Object[]{null, x});
        }
        return rowData;
    }

    // From message compendium
    private static final int DEFAULT = 0x0;
    private static final int DESCENDING = 0x1;
    private static final int START_EXCLUSIVE = 0x2;
    private static final int END_EXCLUSIVE = 0x4;
    private static final int SINGLE_ROW = 0x8;
    private static final int PREFIX = 0x10;
    private static final int START_AT_EDGE = 0x20;
    private static final int END_AT_EDGE = 0x40;
    private static final int DEEP = 0x80;

    private int rowDefId;
}