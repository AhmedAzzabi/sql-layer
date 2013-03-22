
package com.akiban.server.test.it.rowtests;

import com.akiban.server.error.InvalidOperationException;
import com.akiban.server.test.it.ITBase;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

public class NullFieldIT extends ITBase
{
    private final String SCHEMA = "test";
    private final String TABLE = "t";
    private final boolean IS_PK = false ;
    private final boolean INDEXES = true;

    @Test
    public void intEncoder() throws InvalidOperationException {
        final int tid = createTableFromTypes(SCHEMA, TABLE, IS_PK, INDEXES, "int");
        writeRows(createNewRow(tid, 1, 10), createNewRow(tid, 2, null));
    }

    @Test
    public void uintEncoder() throws InvalidOperationException {
        final int tid = createTableFromTypes(SCHEMA, TABLE, IS_PK, INDEXES, "int unsigned");
        writeRows(createNewRow(tid, 1, 10), createNewRow(tid, 2, null));
    }
    
    @Test
    public void ubigintEncoder() throws InvalidOperationException {
        final int tid = createTableFromTypes(SCHEMA, TABLE, IS_PK, INDEXES, "bigint unsigned");
        writeRows(createNewRow(tid, 1, BigInteger.valueOf(10)), createNewRow(tid, 2, null));
    }

    @Test
    public void floatEncoder() throws InvalidOperationException {
        final int tid = createTableFromTypes(SCHEMA, TABLE, IS_PK, INDEXES, "float");
        writeRows(createNewRow(tid, 1, 1.142), createNewRow(tid, 2, null));
    }

    @Test
    public void ufloatEncoder() throws InvalidOperationException {
        final int tid = createTableFromTypes(SCHEMA, TABLE, IS_PK, INDEXES, "float unsigned");
        writeRows(createNewRow(tid, 1, 1.42), createNewRow(tid, 2, null));
    }

    @Test
    public void decimalEncoder() throws InvalidOperationException {
        final int tid = createTableFromTypes(SCHEMA, TABLE, IS_PK, INDEXES, new SimpleColumn("c1", "decimal", 10L, 2L));
        writeRows(createNewRow(tid, 1, BigDecimal.valueOf(110, 2)), createNewRow(tid, 2, null));
    }

    @Test
    public void doubleEncoder() throws InvalidOperationException {
        final int tid = createTableFromTypes(SCHEMA, TABLE, IS_PK, INDEXES, "double");
        writeRows(createNewRow(tid, 1, 1.142), createNewRow(tid, 2, null));
    }

    @Test
    public void udoubleEncoder() throws InvalidOperationException {
        final int tid = createTableFromTypes(SCHEMA, TABLE, IS_PK, INDEXES, "double unsigned");
        writeRows(createNewRow(tid, 1, 1.42), createNewRow(tid, 2, null));
    }

    @Test
    public void stringEncoder() throws InvalidOperationException {
        final int tid = createTableFromTypes(SCHEMA, TABLE, IS_PK, INDEXES, new SimpleColumn("c1", "varchar", 32L, null));
        writeRows(createNewRow(tid, 1, "hello"), createNewRow(tid, 2, null));
    }

    @Test
    public void varbinaryEncoder() throws InvalidOperationException {
        final int tid = createTableFromTypes(SCHEMA, TABLE, IS_PK, INDEXES, new SimpleColumn("c1", "varbinary", 32L, null));
        writeRows(createNewRow(tid, 1, new byte[]{0x71,0x65}), createNewRow(tid, 2, null));
    }

    @Test
    public void dateEncoder() throws InvalidOperationException {
        final int tid = createTableFromTypes(SCHEMA, TABLE, IS_PK, INDEXES, "date");
        writeRows(createNewRow(tid, 1, "2011-04-20"), createNewRow(tid, 2, null));
    }

    @Test
    public void timeEncoder() throws InvalidOperationException {
        final int tid = createTableFromTypes(SCHEMA, TABLE, IS_PK, INDEXES, "time");
        writeRows(createNewRow(tid, 1, "14:10:00"), createNewRow(tid, 2, null));
    }

    @Test
    public void datetimeEncoder() throws InvalidOperationException {
        final int tid = createTableFromTypes(SCHEMA, TABLE, IS_PK, INDEXES, "datetime");
        writeRows(createNewRow(tid, 1, "2011-04-20 14:11:00"), createNewRow(tid, 2, null));
    }

    @Test
    public void timestampEncoder() throws InvalidOperationException {
        final int tid = createTableFromTypes(SCHEMA, TABLE, IS_PK, INDEXES, "timestamp");
        writeRows(createNewRow(tid, 1, "2011-04-20 14:11:47"), createNewRow(tid, 2, null));
    }

    @Test
    public void yearEncoder() throws InvalidOperationException {
        final int tid = createTableFromTypes(SCHEMA, TABLE, IS_PK, INDEXES, "year");
        writeRows(createNewRow(tid, 1, "2011"), createNewRow(tid, 2, null));
    }
}
