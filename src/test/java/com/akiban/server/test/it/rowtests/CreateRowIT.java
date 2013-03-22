
package com.akiban.server.test.it.rowtests;

import com.akiban.server.rowdata.RowData;
import com.akiban.server.rowdata.RowDef;
import com.akiban.server.encoding.EncodingException;
import com.akiban.server.error.InvalidOperationException;
import com.akiban.server.test.it.ITBase;
import org.junit.Test;

import static junit.framework.Assert.fail;
import static junit.framework.Assert.assertEquals;

public class CreateRowIT extends ITBase
{
    @Test
    public void smallRowCantGrow() throws InvalidOperationException
    {
        int t = createTable("s", "t",
                            "string varchar(100) character set latin1");
        RowDef rowDef = getRowDef(t);
        RowData rowData = new RowData(new byte[RowData.MINIMUM_RECORD_LENGTH + 1 /* null bitmap */ + 5]);
        rowData.createRow(rowDef, new Object[]{"abc"}, false);
    }

    @Test(expected=EncodingException.class)
    public void bigRowCantGrow() throws InvalidOperationException
    {
        int t = createTable("s", "t",
                            "string varchar(100)");
        RowDef rowDef = getRowDef(t);
        RowData rowData = new RowData(new byte[RowData.MINIMUM_RECORD_LENGTH + 1 /* null bitmap */ + 5]);
        rowData.createRow(rowDef, new Object[]{"abcdefghijklmnopqrstuvwxyz"}, false);
        fail();
    }

    @Test
    public void growALittle() throws InvalidOperationException
    {
        // Buffer should grow one time
        int t = createTable("s", "t",
                            "string varchar(100)");
        RowDef rowDef = getRowDef(t);
        int initialBufferSize = RowData.MINIMUM_RECORD_LENGTH + 1 /* null bitmap */ + 5;
        RowData rowData = new RowData(new byte[initialBufferSize]);
        rowData.createRow(rowDef, new Object[]{"abcdefghijklmno"}, true);
        assertEquals(initialBufferSize * 2, rowData.getBytes().length);
    }

    @Test
    public void growALot() throws InvalidOperationException
    {
        // Buffer should grow two times
        int t = createTable("s", "t",
                            "string varchar(100)");
        RowDef rowDef = getRowDef(t);
        int initialBufferSize = RowData.MINIMUM_RECORD_LENGTH + 1 /* null bitmap */ + 5;
        RowData rowData = new RowData(new byte[initialBufferSize]);
        // initialBufferSize has room for varchar of size 4, (1 byte of the 5 is for length).
        // initialBufferSize is 24:
        assertEquals(24, initialBufferSize);
        // Doubling it leaves room for 28 chars. Try something a little bigger than that.
        rowData.createRow(rowDef, new Object[]{"abcdefghijklmnopqrstuvwxyz0123"}, true);
        assertEquals(initialBufferSize * 4, rowData.getBytes().length);
    }
}
