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

package com.akiban.server.test.it.rowtests;

import com.akiban.ais.model.Index;
import com.akiban.ais.model.IndexColumn;
import com.akiban.ais.model.Table;
import com.akiban.server.FieldDef;
import com.akiban.server.RowDef;
import com.akiban.server.api.ddl.UnsupportedIndexDataTypeException;
import com.akiban.server.api.dml.scan.NewRow;
import com.akiban.server.store.IndexRecordVisitor;
import com.akiban.server.test.it.ITBase;
import com.persistit.Key;
import junit.framework.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class KeyToObjectIT extends ITBase {

    /**
     * Internal helper for comparing all indexed values in an index tree to their values in the row after
     * going through Encoder.toObject(RowData) and Encoder.toObject(Key), respectively.
     * <p><b>Note:</b> For test simplicity, the values in the row must be in index order.</p>
     * @param tableId Table to scan.
     * @param expectedRowCount Rows expected from a full table scan.
     * @param indexName Name of index to compare to.
     * @throws Exception On error.
     */
    private void testKeyToObject(int tableId, int expectedRowCount, String indexName) throws Exception {
        final Table table = getUserTable(tableId);
        final Index index = table.getIndex(indexName);
        assertNotNull("expected index named: "+indexName, index);
        
        final List<NewRow> allRows = scanAll(scanAllRequest(tableId));
        assertEquals("rows scanned", expectedRowCount, allRows.size());

        final RowDef rowDef = (RowDef)table.rowDef();
        final Iterator<NewRow> rowIt = allRows.iterator();

        persistitStore().traverse(session(), index, new IndexRecordVisitor() {
            private int rowCounter = 0;

            @Override
            protected void visit(List<Object> _) {
                if(!rowIt.hasNext()) {
                    Assert.fail("More index entries than rows: rows("+allRows+") index("+index+")");
                }

                final NewRow row = rowIt.next();
                final Key key = this.exchange.getKey();
                key.indexTo(0);
                
                for(IndexColumn indexColumn : index.getColumns()) {
                    int colPos = indexColumn.getColumn().getPosition();
                    FieldDef fieldDef = rowDef.getFieldDef(colPos);
                    Object objFromRow = row.get(colPos);
                    Object objFromKey = fieldDef.getEncoding().toObject(key);
                    // Work around for dropping of 0 value sigfigs from key.decode()
                    int compareValue = 1;
                    if(objFromRow instanceof BigDecimal && objFromKey instanceof BigDecimal) {
                        compareValue = ((BigDecimal)objFromRow).compareTo(((BigDecimal)objFromKey));
                    }
                    if(compareValue != 0) {
                        assertEquals(String.format("column %d of row %d, row value vs index entry", colPos, rowCounter),
                                     objFromRow, objFromKey);
                    }
                    ++rowCounter;
                }
            }
        });

        if(rowIt.hasNext()) {
            Assert.fail("More rows than index entries: rows("+allRows+") index("+index+")");
        }
    }

    void createAndWriteRows(int tableId, Object[] singleColumnValue) {
        int i = 0;
        for(Object o : singleColumnValue) {
            dml().writeRow(session(), createNewRow(tableId, i++, o));
        }
    }


    @Test
    public void intField() throws Exception {
        final int tid = createTable("test", "t", "id int key", "c2 int, key(c2)");
        Integer values[] = {null, -89573, -10, 0, 1, 42, 1337, 29348291};
        createAndWriteRows(tid, values);
        testKeyToObject(tid, values.length, "c2");
    }

    @Test
    public void intUnsignedField() throws Exception {
        final int tid = createTable("test", "t", "id int key", "c2 int unsigned, key(c2)");
        Integer values[] = {null, 0, 1, 255, 400, 674532, 16777215, 2147483647};
        createAndWriteRows(tid, values);
        testKeyToObject(tid, values.length, "c2");
    }
    
    @Test
    public void floatField() throws Exception {
        final int tid = createTable("test", "t", "id int key", "c2 float, key(c2)");
        Float values[] = {null, -Float.MAX_VALUE, -1337.4356f, -10f, -Float.MIN_VALUE,
                          0f, Float.MIN_VALUE, 1f, 432.235f, 829483.3125f, Float.MAX_VALUE};
        createAndWriteRows(tid, values);
        testKeyToObject(tid, values.length, "c2");
    }

    @Test
    public void floatUnsignedField() throws Exception {
        final int tid = createTable("test", "t", "id int key", "c2 float unsigned, key(c2)");
        Float values[] = {null, 0f, Float.MIN_VALUE, 1f, 42.24f, 829483.3125f, 1234567f, Float.MAX_VALUE};
        createAndWriteRows(tid, values);
        testKeyToObject(tid, values.length, "c2");
    }

    @Test
    public void doubleField() throws Exception {
        final int tid = createTable("test", "t", "id int key", "c2 double, key(c2)");
        Double values[] = {null, -Double.MAX_VALUE, -849284.284, -5d, -Double.MIN_VALUE,
                           0d, Double.MIN_VALUE, 1d, 100d, 9128472947.284729, Double.MAX_VALUE};
        createAndWriteRows(tid, values);
        testKeyToObject(tid, values.length, "c2");
    }

    @Test
    public void doubleUnsignedField() throws Exception {
        final int tid = createTable("test", "t", "id int key", "c2 double unsigned, key(c2)");
        Double values[] = {null, 0d, Double.MIN_VALUE, 1d, 8587d, 123456.789d, 9879679567.284729, Double.MAX_VALUE};
        createAndWriteRows(tid, values);
        testKeyToObject(tid, values.length, "c2");
    }

    @Test
    public void decimalField() throws Exception {
        final int tid = createTable("test", "t", "id int key", "c2 decimal(5,2), key(c2)");
        BigDecimal values[] = {null, BigDecimal.valueOf(-99999, 2), BigDecimal.valueOf(-999),
                               BigDecimal.valueOf(-1234, 1), BigDecimal.valueOf(0), BigDecimal.valueOf(1),
                               BigDecimal.valueOf(426), BigDecimal.valueOf(5678, 1), BigDecimal.valueOf(99999, 2)};
        createAndWriteRows(tid, values);
        testKeyToObject(tid, values.length, "c2");
    }

    @Test
    public void decimalUnsignedField() throws Exception {
        final int tid = createTable("test", "t", "id int key", "c2 decimal(5,2) unsigned, key(c2)");
        BigDecimal values[] = {null, BigDecimal.valueOf(0), BigDecimal.valueOf(1), BigDecimal.valueOf(4242, 2),
                               BigDecimal.valueOf(5678, 1), BigDecimal.valueOf(99999, 2)};
        createAndWriteRows(tid, values);
        testKeyToObject(tid, values.length, "c2");
    }

    @Test
    public void charField() throws Exception {
        final int tid = createTable("test", "t", "id int key", "c2 char(10), key(c2)");
        String values[] = {null, "", "0123456789", "zebra"};
        createAndWriteRows(tid, values);
        testKeyToObject(tid, values.length, "c2");
    }

    @Test
    public void varcharField() throws Exception {
        final int tid = createTable("test", "t", "id int key", "c2 varchar(26), key(c2)");
        String values[] = {null, "", "abcdefghijklmnopqrstuvwxyz", "see spot run"};
        createAndWriteRows(tid, values);
        testKeyToObject(tid, values.length, "c2");
    }

    @Test(expected=UnsupportedIndexDataTypeException.class)
    public void blobField() throws Exception {
        final int tid = createTable("test", "t", "id int key", "c2 blob, key(c2)");
    }

    @Test(expected=UnsupportedIndexDataTypeException.class)
    public void textField() throws Exception {
        createTable("test", "t", "id int key", "c2 text, key(c2)");
    }

    @Test
    public void binaryField() throws Exception {
        final int tid = createTable("test", "t", "id int key", "c2 binary(10), key(c2)");
        byte[][] values = {null, {}, {1,2,3,4,5}, {-24, 8, -98, 45, 67, 127, 34, -42, 9, 10}};
        createAndWriteRows(tid, values);
        testKeyToObject(tid, values.length, "c2");
    }

    @Test
    public void varbinaryField() throws Exception {
        final int tid = createTable("test", "t", "id int key", "c2 varbinary(26), key(c2)");
        byte[][] values = {null, {}, {11,7,5,2}, {-24, 8, -98, 45, 67, 127, 34, -42, 9, 10, 29, 75, 127, -125, 5, 52}};
        createAndWriteRows(tid, values);
        testKeyToObject(tid, values.length, "c2");
    }

    @Test
    public void dateField() throws Exception {
        final int tid = createTable("test", "t", "id int key", "c2 date, key(c2)");
        String values[] = {null, "0000-00-00", "1000-01-01", "2011-05-20", "9999-12-31"};
        createAndWriteRows(tid, values);
        testKeyToObject(tid, values.length, "c2");
    }

    @Test
    public void datetimeField() throws Exception {
        final int tid = createTable("test", "t", "id int key", "c2 datetime, key(c2)");
        String values[] = {null, "0000-00-00 00:00:00", "1000-01-01 00:00:00", "2011-05-20 17:35:01", "9999-12-31 23:59:59"};
        createAndWriteRows(tid, values);
        testKeyToObject(tid, values.length, "c2");
    }

    @Test
    public void timeField() throws Exception {
        final int tid = createTable("test", "t", "id int key", "c2 time, key(c2)");
        String values[] = {null, "-838:59:59", "00:00:00", "17:34:20", "838:59:59"};
        createAndWriteRows(tid, values);
        testKeyToObject(tid, values.length, "c2");
    }

    @Test
    public void timestampField() throws Exception {
        final int tid = createTable("test", "t", "id int key", "c2 timestamp, key(c2)");
        Long values[] = {null, 0L, 1305927301L, 2147483647L};
        createAndWriteRows(tid, values);
        testKeyToObject(tid, values.length, "c2");
    }

    @Test
    public void yearField() throws Exception {
        final int tid = createTable("test", "t", "id int key", "c2 year, key(c2)");
        String values[] = {null, "0000", "1901", "2011", "2155"};
        createAndWriteRows(tid, values);
        testKeyToObject(tid, values.length, "c2");
    }
}
