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

package com.akiban.cserver.api.dml.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.junit.Test;

import com.akiban.cserver.RowData;
import com.akiban.cserver.RowDef;
import com.akiban.cserver.RowDefCache;
import com.akiban.cserver.SchemaFactory;
import com.akiban.cserver.api.common.ColumnId;
import com.akiban.cserver.api.common.TableId;

public final class NiceRowTest {
    @Test
    public void toRowDataBasic() throws Exception
    {
        RowDef rowDef = createRowDef(2);

        Object[] objects = new Object[2];
        objects[0] = 5;
        objects[1] = "Bob";

        RowData rowData = create(rowDef, objects);

        NewRow newRow = NiceRow.fromRowData(rowData, rowDef);

        // Why -1: because an __akiban_pk column gets added
        assertEquals("fields count", 2, newRow.getFields().size() - 1);
        assertEquals("field[0]", 5L, newRow.get(ColumnId.of(0)));
        assertEquals("field[1]", "Bob", newRow.get(ColumnId.of(1)));

        compareRowDatas(rowData, newRow.toRowData());
    }

    @Test
    public void toRowDataLarge() throws Exception
    {
        final int NUM = 30;
        RowDef rowDef = createRowDef(NUM);

        Object[] objects = new Object[NUM];
        objects[0] = 15;
        objects[1] = "Robert";
        for (int i=2; i < NUM; ++i) {
            objects[i] = i + 1000;
        }

        RowData rowData = create(rowDef, objects);

        NewRow newRow = NiceRow.fromRowData(rowData, rowDef);

        // Why -1: because an __akiban_pk column gets added
        assertEquals("fields count", NUM, newRow.getFields().size() - 1);
        assertEquals("field[0]", 15L, newRow.get(ColumnId.of(0)));
        assertEquals("field[1]", "Robert", newRow.get(ColumnId.of(1)));
        for (int i=2; i < NUM; ++i) {
            long expected = i + 1000;
            assertEquals("field[1]", expected, newRow.get(ColumnId.of(i)));
        }

        compareRowDatas(rowData, newRow.toRowData());
    }

    @Test
    public void toRowDataSparse() throws Exception
    {
        final int NUM = 30;
        RowDef rowDef = createRowDef(NUM);

        Object[] objects = new Object[NUM];
        objects[0] = 15;
        objects[1] = "Robert";
        int nulls = 0;
        for (int i=2; i < NUM; ++i) {
            if ( (i % 3) == 0) {
                ++nulls;
            }
            else {
                objects[i] = i + 1000;
            }
        }
        assertTrue("nulls==0", nulls > 0);

        RowData rowData = create(rowDef, objects);

        NewRow newRow = NiceRow.fromRowData(rowData, rowDef);

        // Why -1: because an __akiban_pk column gets added
        assertEquals("fields count", NUM, newRow.getFields().size() - 1);
        assertEquals("field[0]", 15L, newRow.get(ColumnId.of(0)));
        assertEquals("field[1]", "Robert", newRow.get(ColumnId.of(1)));
        for (int i=2; i < NUM; ++i) {
            Long expected = (i % 3) == 0 ? null : i + 1000L;
            assertEquals("field[1]", expected, newRow.get(ColumnId.of(i)));
        }

        compareRowDatas(rowData, newRow.toRowData());
    }

    @Test
    public void testEquality() {
        TreeMap<Integer,NiceRow> mapOne = new TreeMap<Integer, NiceRow>();
        TreeMap<Integer,NiceRow> mapTwo = new TreeMap<Integer, NiceRow>();
        NiceRow rowOne = new NiceRow(TableId.of(1), null);
        rowOne.put(ColumnId.of(0), Long.valueOf(0l));
        rowOne.put(ColumnId.of(1), "hello world");
        mapOne.put(0, rowOne);

        NiceRow rowTwo = new NiceRow(TableId.of(1), null);
        rowTwo.put(ColumnId.of(0), Long.valueOf(0l));
        rowTwo.put(ColumnId.of(1), "hello world");
        mapTwo.put(0, rowTwo);

        assertEquals("rows", rowOne, rowTwo);
        assertEquals("maps", mapOne, mapTwo);
    }

    private static byte[] bytes() {
        return new byte[1024];
    }

    private static RowDef createRowDef(int totalColumns) throws Exception {
        assertTrue("bad totalColumns=" + totalColumns, totalColumns >= 2);
        String[] ddl = new String[totalColumns + 3];
        int i = 0;
        ddl[i++] = "use test_schema; ";
        ddl[i++] = "create table test_table(";
        ddl[i++] = "id int";
        ddl[i++] = ", name varchar(128)";
        for (int c = 2; c < totalColumns; c++) {
            ddl[i++] = String.format(", field_%s int", c);
        }
        ddl[i] = ") engine = akibandb;";
        RowDefCache rowDefCache = SCHEMA_FACTORY.rowDefCache(ddl);
        return rowDefCache.getRowDef("test_schema.test_table");
    }

    private RowData create(RowDef rowDef, Object[] objects) {
        RowData rowData = new RowData(bytes());
        rowData.createRow(rowDef, objects);

        assertEquals("start", 0, rowData.getBufferStart());
        assertEquals("end and length", rowData.getBufferEnd(), rowData.getBufferLength());
        return rowData;
    }

    private void compareRowDatas(RowData expected, RowData actual) {
        if (expected == actual) {
            return;
        }

        List<Byte> expectedBytes = byteListFor(expected);
        List<Byte> actualBytes = byteListFor(actual);
        assertEquals("bytes", expectedBytes, actualBytes);
    }

    private List<Byte> byteListFor(RowData rowData) {
        byte[] bytes = rowData.getBytes();
        assertNotNull("RowData bytes[] null", bytes);
        assertTrue("start < 0: " + rowData.getRowStart(), rowData.getRowStart() >= 0);
        assertTrue("end out of range: " + rowData.getRowEnd(), rowData.getRowEnd() <= bytes.length);

        List<Byte> bytesList = new ArrayList<Byte>();
        for (int i=rowData.getBufferStart(), MAX=rowData.getRowEnd(); i < MAX; ++i) {
            bytesList.add(bytes[i]);
        }
        return bytesList;
    }

    private static final SchemaFactory SCHEMA_FACTORY = new SchemaFactory();
}
