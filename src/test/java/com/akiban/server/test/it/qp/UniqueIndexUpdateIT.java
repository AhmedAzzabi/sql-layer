
package com.akiban.server.test.it.qp;

import com.akiban.qp.operator.Cursor;
import com.akiban.qp.operator.Operator;
import com.akiban.qp.persistitadapter.indexrow.PersistitIndexRowBuffer;
import com.akiban.qp.row.Row;
import com.akiban.qp.rowtype.IndexRowType;
import com.akiban.qp.rowtype.Schema;
import com.akiban.qp.rowtype.UserTableRowType;
import com.akiban.server.api.dml.scan.NewRow;
import org.junit.Before;
import org.junit.Test;

import static com.akiban.qp.operator.API.cursor;
import static com.akiban.qp.operator.API.indexScan_Default;
import static org.junit.Assert.*;

public class UniqueIndexUpdateIT extends OperatorITBase
{
    @Override
    protected void setupCreateSchema()
    {
        t = createTable(
            "schema", "t",
            "id int not null",
            "x int",
            "y int",
            "primary key (id)");
        createUniqueIndex("schema", "t", "idx_xy", "x", "y");
    }

    @Override
    protected void setupPostCreateSchema()
    {
        schema = new Schema(ais());
        tRowType = schema.userTableRowType(userTable(t));
        xyIndexRowType = indexType(t, "x", "y");
        adapter = persistitAdapter(schema);
        queryContext = queryContext(adapter);
    }

    @Test
    public void testNullSeparatorOnInsert()
    {
        dml().writeRow(session(), createNewRow(t, 1000L, 1L, 1L));
        dml().writeRow(session(), createNewRow(t, 2000L, 2L, 2L));
        dml().writeRow(session(), createNewRow(t, 3000L, 3L, null));
        dml().writeRow(session(), createNewRow(t, 4000L, 4L, null));
        Operator plan = indexScan_Default(xyIndexRowType);
        Cursor cursor = cursor(plan, queryContext);
        cursor.open();
        Row row;
        // Need to examine PIRBs to see null separators
        int count = 0;
        long previousNullSeparator = -1L;
        while ((row = cursor.next()) != null) {
            PersistitIndexRowBuffer indexRow = (PersistitIndexRowBuffer) row;
            long x = getLong(indexRow, 0);
            long id = getLong(indexRow, 2);
            assertEquals(id, x * 1000);
            long nullSeparator = indexRow.nullSeparator();
            if (isNull(indexRow, 1)) {
                assertTrue(id == 3000 || id == 4000);
                assertTrue(nullSeparator > 0);
                assertTrue(nullSeparator != previousNullSeparator);
            } else {
                assertTrue(id == 1000 || id == 2000);
                assertEquals(0, nullSeparator);
            }
            count++;
        }
        assertEquals(4, count);
    }

    @Test
    public void testNullSeparatorOnUpdate()
    {
        // Load as in testNullSeparatorOnInsert
        dml().writeRow(session(), createNewRow(t, 1000L, 1L, 1L));
        dml().writeRow(session(), createNewRow(t, 2000L, 2L, 2L));
        dml().writeRow(session(), createNewRow(t, 3000L, 3L, null));
        dml().writeRow(session(), createNewRow(t, 4000L, 4L, null));
        // Change nulls to some other value. Scan backwards to avoid halloween issues.
        Cursor cursor = cursor(indexScan_Default(xyIndexRowType, true), queryContext);
        cursor.open();
        Row row;
        final long NEW_Y_VALUE = 99;
        while ((row = cursor.next()) != null) {
            PersistitIndexRowBuffer indexRow = (PersistitIndexRowBuffer) row;
            long x = getLong(indexRow, 0);
            long id = getLong(indexRow, 2);
            int pos = 1;
            if (isNull(indexRow, pos)) {
                NewRow oldRow = createNewRow(t, id, x, null);
                NewRow newRow = createNewRow(t, id, x, NEW_Y_VALUE);
                dml().updateRow(session(), oldRow, newRow, null);
            }
        }
        cursor.close();
        // Check final state
        cursor = cursor(indexScan_Default(xyIndexRowType), queryContext);
        cursor.open();
        // Need to examine PIRBs to see null separators
        int count = 0;
        while ((row = cursor.next()) != null) {
            PersistitIndexRowBuffer indexRow = (PersistitIndexRowBuffer) row;
            long x = getLong(indexRow, 0);
            long y = getLong(indexRow, 1);
            long id = getLong(indexRow, 2);
            long nullSeparator = indexRow.nullSeparator();
            assertEquals(id, x * 1000);
            assertEquals(0, nullSeparator);
            if (id <= 2000) {
                assertEquals(id, y * 1000);
            } else {
                assertEquals(NEW_Y_VALUE, y);
            }
            count++;
        }
        assertEquals(4, count);
    }

    @Test
    public void testDeleteIndexRowWithNull()
    {
        dml().writeRow(session(), createNewRow(t, 1L, 999L, null));
        dml().writeRow(session(), createNewRow(t, 2L, 999L, null));
        dml().writeRow(session(), createNewRow(t, 3L, 999L, null));
        dml().writeRow(session(), createNewRow(t, 4L, 999L, null));
        dml().writeRow(session(), createNewRow(t, 5L, 999L, null));
        dml().writeRow(session(), createNewRow(t, 6L, 999L, null));
        checkIndex(1, 2, 3, 4, 5, 6);
        // Delete each row
        dml().deleteRow(session(), createNewRow(t, 3L, 999L, null), false);
        checkIndex(1, 2, 4, 5, 6);
        dml().deleteRow(session(), createNewRow(t, 6L, 999L, null), false);
        checkIndex(1, 2, 4, 5);
        dml().deleteRow(session(), createNewRow(t, 2L, 999L, null), false);
        checkIndex(1, 4, 5);
        dml().deleteRow(session(), createNewRow(t, 4L, 999L, null), false);
        checkIndex(1, 5);
        dml().deleteRow(session(), createNewRow(t, 1L, 999L, null), false);
        checkIndex(5);
        dml().deleteRow(session(), createNewRow(t, 5L, 999L, null), false);
        checkIndex();
    }

    private void checkIndex(long ... expectedIds)
    {
        Cursor cursor = cursor(indexScan_Default(xyIndexRowType), queryContext);
        cursor.open();
        Row row;
        int count = 0;
        while ((row = cursor.next()) != null) {
            long id = getLong(row, 2);
            assertEquals(expectedIds[count], id);
            count++;
        }
        assertEquals(expectedIds.length, count);
    }

    // Inspired by bug 1036389

    @Test
    public void testUpdateIndexRowWithNull()
    {
        db = new NewRow[]{
            createNewRow(t, 1L, null, null),
        };
        use(db);
        NewRow oldRow = createNewRow(t, 1L, null, null);
        NewRow newRow = createNewRow(t, 1L, 10L, 10L);
        dml().updateRow(session(), oldRow, newRow, null);
        Cursor cursor = cursor(indexScan_Default(xyIndexRowType), queryContext);
        cursor.open();
        Row row = cursor.next();
        assertEquals(Long.valueOf(10), getLong(row, 0));
        assertEquals(Long.valueOf(10), getLong(row, 1));
        assertEquals(Long.valueOf(1), getLong(row, 2));
        row = cursor.next();
        assertNull(row);
    }

    private int t;
    private UserTableRowType tRowType;
    private IndexRowType xyIndexRowType;
}
