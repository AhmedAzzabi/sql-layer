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

package com.akiban.server.mttests.mtatomics;

import com.akiban.ais.model.Index;
import com.akiban.ais.model.TableName;
import com.akiban.ais.model.UserTable;
import com.akiban.server.InvalidOperationException;
import com.akiban.server.api.common.NoSuchTableException;
import com.akiban.server.api.dml.NoSuchIndexException;
import com.akiban.server.api.dml.scan.CursorId;
import com.akiban.server.api.dml.scan.NewRow;
import com.akiban.server.api.dml.scan.ScanAllRequest;
import com.akiban.server.api.dml.scan.ScanFlag;
import com.akiban.server.api.dml.scan.ScanLimit;
import com.akiban.server.mttests.mtutil.TimePoints;
import com.akiban.server.mttests.mtutil.TimePointsComparison;
import com.akiban.server.mttests.mtutil.TimedCallable;
import com.akiban.server.mttests.mtutil.TimedExceptionCatcher;
import com.akiban.server.mttests.mtutil.Timing;
import com.akiban.server.mttests.mtutil.TimedResult;
import com.akiban.server.service.dxl.ConcurrencyAtomicsDXLService;
import com.akiban.server.service.session.Session;
import com.akiban.server.service.session.SessionImpl;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class ConcurrentDDLAtomicsMT extends ConcurrentAtomicsBase {

    @Test
    public void dropTableWhileScanningPK() throws Exception {
        final int tableId = tableWithTwoRows();
        dropTableWhileScanning(
                tableId,
                "PRIMARY",
                createNewRow(tableId, 1L, "the snowman"),
                createNewRow(tableId, 2L, "mr melty")
        );
    }

    @Test
    public void dropTableWhileScanningOnIndex() throws Exception {
        final int tableId = tableWithTwoRows();
        dropTableWhileScanning(
                tableId,
                "name",
                createNewRow(tableId, 2L, "mr melty"),
                createNewRow(tableId, 1L, "the snowman")
        );
    }

    private void dropTableWhileScanning(int tableId, String indexName, NewRow... expectedScanRows) throws Exception {
        final int SCAN_WAIT = 5000;

        int indexId = ddl().getUserTable(session(), new TableName(SCHEMA, TABLE)).getIndex(indexName).getIndexId();

        TimedCallable<List<NewRow>> scanCallable
                = new DelayScanCallableBuilder(tableId, indexId).topOfLoopDelayer(0, SCAN_WAIT, "SCAN: PAUSE").get(ddl());

        TimedCallable<Void> dropIndexCallable = new TimedCallable<Void>() {
            @Override
            protected Void doCall(TimePoints timePoints, Session session) throws Exception {
                TableName table = new TableName(SCHEMA, TABLE);
                Timing.sleep(2000);
                timePoints.mark("TABLE: DROP>");
                ddl().dropTable(session, table);
                timePoints.mark("TABLE: <DROP");
                return null;
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<TimedResult<List<NewRow>>> scanFuture = executor.submit(scanCallable);
        Future<TimedResult<Void>> dropIndexFuture = executor.submit(dropIndexCallable);

        TimedResult<List<NewRow>> scanResult = scanFuture.get();
        TimedResult<Void> dropIndexResult = dropIndexFuture.get();

        new TimePointsComparison(scanResult, dropIndexResult).verify(
                "SCAN: START",
                "(SCAN: PAUSE)>",
                "TABLE: DROP>",
                "<(SCAN: PAUSE)",
                "SCAN: FINISH",
                "TABLE: <DROP"
        );

        List<NewRow> rowsScanned = scanResult.getItem();
        assertEquals("rows scanned size", expectedScanRows.length, rowsScanned.size());
        assertEquals("rows", Arrays.asList(expectedScanRows), rowsScanned);
    }

    @Test
    public void rowConvertedAfterTableDrop() throws Exception {
        final String index = "PRIMARY";
        final int tableId = tableWithTwoRows();
        final int indexId = ddl().getUserTable(session(), new TableName(SCHEMA, TABLE)).getIndex(index).getIndexId();

        DelayScanCallableBuilder callableBuilder = new DelayScanCallableBuilder(tableId, indexId)
                .markFinish(false)
                .beforeConversionDelayer(new DelayerFactory() {
                    @Override
                    public Delayer delayer(TimePoints timePoints) {
                        return new Delayer(timePoints, 0, 5000)
                                .markBefore(1, "SCAN: PAUSE")
                                .markAfter(1, "SCAN: CONVERTED");
                    }
                });
        TimedCallable<List<NewRow>> scanCallable = callableBuilder.get(ddl());

        TimedCallable<Void> dropIndexCallable = new TimedCallable<Void>() {
            @Override
            protected Void doCall(TimePoints timePoints, Session session) throws Exception {
                TableName table = new TableName(SCHEMA, TABLE);
                Timing.sleep(2000);
                timePoints.mark("TABLE: DROP>");
                ddl().dropTable(session, table);
                timePoints.mark("TABLE: <DROP");
                return null;
            }
        };

        // Has to happen before the table is dropped!
        List<NewRow> rowsExpected = Arrays.asList(
                createNewRow(tableId, 1L, "the snowman"),
                createNewRow(tableId, 2L, "mr melty")
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<TimedResult<List<NewRow>>> scanFuture = executor.submit(scanCallable);
        Future<TimedResult<Void>> dropIndexFuture = executor.submit(dropIndexCallable);

        TimedResult<List<NewRow>> scanResult = scanFuture.get();
        TimedResult<Void> dropIndexResult = dropIndexFuture.get();

        new TimePointsComparison(scanResult, dropIndexResult).verify(
                "SCAN: START",
                "SCAN: PAUSE",
                "TABLE: DROP>",
                "SCAN: CONVERTED",
                "TABLE: <DROP"
        );

        List<NewRow> rowsScanned = scanResult.getItem();
        assertEquals("rows scanned (in order)", rowsExpected, rowsScanned);
    }

    @Test
    public void scanPKWhileDropping() throws Exception {
        scanWhileDropping("PRIMARY");
    }

    @Test
    public void scanIndexWhileDropping() throws Exception {
        scanWhileDropping("name");
    }

    @Test
    public void dropShiftsIndexIdWhileScanning() throws Exception {
        final int tableId = createTable(SCHEMA, TABLE, "id int key", "name varchar(32)", "age varchar(2)", "key(name)", "key(age)");
        writeRows(
                createNewRow(tableId, 2, "alpha", 3),
                createNewRow(tableId, 1, "bravo", 2),
                createNewRow(tableId, 3, "charlie", 1)
                // the above are listed in order of index #1 (the name index)
                // after that index is dropped, index #1 is age, and that will come in this order:
                // (3, charlie 1)
                // (1, bravo, 2)
                // (2, alpha, 3)
                // We'll get to the 2nd index (bravo) when we drop the index, and we want to make sure we don't
                // continue scanning with alpha (which would thus badly order name)
        );
        final TableName tableName = new TableName(SCHEMA, TABLE);
        Index nameIndex = ddl().getUserTable(session(), tableName).getIndex("name");
        Index ageIndex = ddl().getUserTable(session(), tableName).getIndex("age");
        assertTrue("age index's ID relative to name's", ageIndex.getIndexId() == nameIndex.getIndexId() + 1);
        final int nameIndexId = nameIndex.getIndexId();

        TimedCallable<List<NewRow>> scanCallable
                = new DelayScanCallableBuilder(tableId, nameIndexId).topOfLoopDelayer(2, 5000, "SCAN: PAUSE").get(ddl());

        TimedCallable<Void> dropIndexCallable = new TimedCallable<Void>() {
            @Override
            protected Void doCall(TimePoints timePoints, Session session) throws Exception {
                Timing.sleep(2500);
                timePoints.mark("DROP: IN");
                ddl().dropIndexes(session, tableName, Collections.singleton("name"));
                timePoints.mark("DROP: OUT");
                return null;
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<TimedResult<List<NewRow>>> scanFuture = executor.submit(scanCallable);
        Future<TimedResult<Void>> dropIndexFuture = executor.submit(dropIndexCallable);

        TimedResult<List<NewRow>> scanResult = scanFuture.get();
        TimedResult<Void> dropIndexResult = dropIndexFuture.get();

        assertEquals("age's index ID",
                nameIndexId,
                ddl().getUserTable(session(), tableName).getIndex("age").getIndexId().intValue()
        );

        new TimePointsComparison(scanResult, dropIndexResult).verify(
                "SCAN: START",
                "(SCAN: PAUSE)>",
                "DROP: IN",
                "<(SCAN: PAUSE)",
                "SCAN: FINISH",
                "DROP: OUT"
        );

        newRowsOrdered(scanResult.getItem(), 1);
    }

    /**
     * Smoke test of concurrent DDL causing failures. One thread will drop a table; the other one will try to create
     * another table while that drop is still going on.
     * @throws Exception if something went wrong :)
     */
    @Test
    public void createTableWhileDroppingAnother() throws Exception {
        largeEnoughTable(5000);
        final String uniqueTableName = TABLE + "thesnowman";

        TimedCallable<Void> dropTable = new TimedCallable<Void>() {
            @Override
            protected Void doCall(TimePoints timePoints, Session session) throws Exception {
                timePoints.mark("DROP>");
                ddl().dropTable(session, new TableName(SCHEMA, TABLE));
                timePoints.mark("DROP<");
                return null;
            }
        };
        TimedCallable<Void> createTable = new TimedCallable<Void>() {
            @Override
            protected Void doCall(TimePoints timePoints, Session session) throws Exception {
                Timing.sleep(2000);
                timePoints.mark("ADD>");
                try {
                    createTable(SCHEMA, uniqueTableName, "id int key");
                    timePoints.mark("ADD SUCCEEDED");
                } catch (IllegalStateException e) {
                    timePoints.mark("ADD FAILED");
                }
                return null;
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<TimedResult<Void>> dropFuture = executor.submit(dropTable);
        Future<TimedResult<Void>> createFuture = executor.submit(createTable);

        TimedResult<Void> dropResult = dropFuture.get();
        TimedResult<Void> createResult = createFuture.get();

        new TimePointsComparison(dropResult, createResult).verify(
                "DROP>",
                "ADD>",
                "ADD FAILED",
                "DROP<"
        );

        Set<TableName> userTableNames = new HashSet<TableName>();
        for (UserTable userTable : ddl().getAIS(session()).getUserTables().values()) {
            if (!"akiban_information_schema".equals(userTable.getName().getSchemaName())) {
                userTableNames.add(userTable.getName());
            }
        }
        assertEquals(
                "user tables at end",
                Collections.singleton(new TableName(SCHEMA, TABLE+"parent")),
                userTableNames
        );
    }

    private void newRowsOrdered(List<NewRow> rows, final int fieldIndex) {
        assertTrue("not enough rows: " + rows, rows.size() > 1);
        List<NewRow> ordered = new ArrayList<NewRow>(rows);
        Collections.sort(ordered, new Comparator<NewRow>() {
            @Override @SuppressWarnings("unchecked")
            public int compare(NewRow o1, NewRow o2) {
                Object o1Field = o1.getFields().get(fieldIndex);
                Object o2Field = o2.getFields().get(fieldIndex);
                if (o1Field == null) {
                    return o2Field == null ? 0 : -1;
                }
                if (o2Field == null) {
                    return 1;
                }
                Comparable o1Comp = (Comparable)o1Field;
                Comparable o2Comp = (Comparable)o2Field;
                return o1Comp.compareTo(o2Comp);
            }
        });
    }

    private void scanWhileDropping(String indexName) throws InvalidOperationException, InterruptedException, ExecutionException {
        final int tableId = largeEnoughTable(5000);
        final TableName tableName = new TableName(SCHEMA, TABLE);
        final int indexId = ddl().getUserTable(session(), tableName).getIndex(indexName).getIndexId();

        DelayScanCallableBuilder callableBuilder = new DelayScanCallableBuilder(tableId, indexId)
                .topOfLoopDelayer(1, 100, "SCAN: FIRST")
                .initialDelay(2500)
                .markFinish(false);
        DelayableScanCallable scanCallable = callableBuilder.get(ddl());
        TimedCallable<Void> dropCallable = new TimedCallable<Void>() {
            @Override
            protected Void doCall(TimePoints timePoints, Session session) throws Exception {
                timePoints.mark("DROP: IN");
                ddl().dropTable(session, tableName);
                timePoints.mark("DROP: OUT");
                return null;
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<TimedResult<List<NewRow>>> scanFuture = executor.submit(scanCallable);
        Future<TimedResult<Void>> updateFuture = executor.submit(dropCallable);

        try {
            scanFuture.get();
            fail("expected an exception!");
        } catch (ExecutionException e) {
            if (!NoSuchTableException.class.equals(e.getCause().getClass())) {
                throw new RuntimeException("Expected a NoSuchTableException!", e.getCause());
            }
        }
        TimedResult<Void> updateResult = updateFuture.get();

        new TimePointsComparison(updateResult).verify(
                "DROP: IN",
                "DROP: OUT"
        );

        assertTrue("rows weren't empty!", scanCallable.getRows().isEmpty());
    }

    /**
     * Creates a table with enough rows that it takes a while to drop it
     * @param msForDropping how long (at least) it should take to drop this table
     * @return the table's id
     * @throws InvalidOperationException if ever encountered
     */
    private int largeEnoughTable(long msForDropping) throws InvalidOperationException {
        int rowCount;
        long dropTime;
        float factor = 1.5f; // after we write N rows, we'll write an additional (factor-1)*N rows as buffer
        int parentId = createTable(SCHEMA, TABLE+"parent", "id int key");
        writeRows(
                createNewRow(parentId, 1)
        );
        final String[] childTableDDL = {"id int key", "pid int", "name varchar(32)", "key(name)",
                "CONSTRAINT __akiban_p FOREIGN KEY __akiban_p(pid) REFERENCES " +TABLE+"parent(id)"};
        do {
            int tableId = createTable(SCHEMA, TABLE, childTableDDL);
            rowCount = 1;
            final long writeStart = System.currentTimeMillis();
            while (System.currentTimeMillis() - writeStart < msForDropping) {
                writeRows(
                        createNewRow(tableId, rowCount, Integer.toString(rowCount))
                );
                ++rowCount;
            }
            for(int i = rowCount; i < (int) factor * rowCount ; ++i) {
                writeRows(
                        createNewRow(tableId, i, Integer.toString(i))
                );
            }
            final long dropStart = System.currentTimeMillis();
            ddl().dropTable(session(), new TableName(SCHEMA, TABLE));
            dropTime = System.currentTimeMillis() - dropStart;
            factor += 0.2;
        } while(dropTime < msForDropping);

        int tableId = createTable(SCHEMA, TABLE, childTableDDL);
        for(int i = 1; i < rowCount ; ++i) {
            writeRows(
                    createNewRow(tableId, i, Integer.toString(i))
            );
        }

        return tableId;
    }

    @Test
    public void dropIndexWhileScanning() throws Exception {
        final int tableId = tableWithTwoRows();
        final int SCAN_WAIT = 5000;

        int indexId = ddl().getUserTable(session(), new TableName(SCHEMA, TABLE)).getIndex("name").getIndexId();

        TimedCallable<List<NewRow>> scanCallable
                = new DelayScanCallableBuilder(tableId, indexId).topOfLoopDelayer(0, SCAN_WAIT, "SCAN: PAUSE").get(ddl());

        TimedCallable<Void> dropIndexCallable = new TimedCallable<Void>() {
            @Override
            protected Void doCall(TimePoints timePoints, Session session) throws Exception {
                TableName table = new TableName(SCHEMA, TABLE);
                Timing.sleep(2000);
                timePoints.mark("INDEX: DROP>");
                ddl().dropIndexes(new SessionImpl(), table, Collections.singleton("name"));
                timePoints.mark("INDEX: <DROP");
                return null;
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<TimedResult<List<NewRow>>> scanFuture = executor.submit(scanCallable);
        Future<TimedResult<Void>> dropIndexFuture = executor.submit(dropIndexCallable);

        TimedResult<List<NewRow>> scanResult = scanFuture.get();
        TimedResult<Void> dropIndexResult = dropIndexFuture.get();

        new TimePointsComparison(scanResult, dropIndexResult).verify(
                "SCAN: START",
                "(SCAN: PAUSE)>",
                "INDEX: DROP>",
                "<(SCAN: PAUSE)",
                "SCAN: FINISH",
                "INDEX: <DROP"
        );

        List<NewRow> rowsScanned = scanResult.getItem();
        List<NewRow> rowsExpected = Arrays.asList(
                createNewRow(tableId, 2L, "mr melty"),
                createNewRow(tableId, 1L, "the snowman")
        );
        assertEquals("rows scanned (in order)", rowsExpected, rowsScanned);
    }

    @Test
    public void scanWhileDroppingIndex() throws Throwable {
        final long SCAN_PAUSE_LENGTH = 2500;
        final long DROP_START_LENGTH = 1000;
        final long DROP_PAUSE_LENGTH = 2500;


        final int NUMBER_OF_ROWS = 100;
        final int initialTableId = createTable(SCHEMA, TABLE, "id int key", "age int", "key(age)");
        final TableName tableName = new TableName(SCHEMA, TABLE);
        for(int i=0; i < NUMBER_OF_ROWS; ++i) {
            writeRows(createNewRow(initialTableId, i, i + 1));
        }

        final Index index = ddl().getUserTable(session(), tableName).getIndex("age");
        final Collection<String> indexNameCollection = Collections.singleton(index.getIndexName().getName());
        final int tableId = ddl().getTableId(session(), tableName);


        TimedCallable<Throwable> dropIndexCallable = new TimedExceptionCatcher() {
            @Override
            protected void doOrThrow(TimePoints timePoints, Session session) throws Exception {
                Timing.sleep(DROP_START_LENGTH);
                timePoints.mark("DROP: PREPARING");
                ConcurrencyAtomicsDXLService.delayNextDropIndex(session, DROP_PAUSE_LENGTH);

                timePoints.mark("DROP: IN");
                ddl().dropIndexes(session, tableName, indexNameCollection);
                assertFalse("drop hook not removed!", ConcurrencyAtomicsDXLService.isDropIndexDelayInstalled(session));
                timePoints.mark("DROP: OUT");
            }
        };
        TimedCallable<Throwable> scanCallable = new TimedExceptionCatcher() {
            @Override
            protected void doOrThrow(TimePoints timePoints, Session session) throws Exception {
                timePoints.mark("SCAN: PREPARING");

                ScanAllRequest request = new ScanAllRequest(
                        tableId,
                        new HashSet<Integer>(Arrays.asList(0, 1)),
                        index.getIndexId(),
                        EnumSet.of(ScanFlag.START_AT_BEGINNING, ScanFlag.END_AT_END),
                        ScanLimit.NONE
                );

                timePoints.mark("(SCAN: PAUSE)>");
                Timing.sleep(SCAN_PAUSE_LENGTH);
                timePoints.mark("<(SCAN: PAUSE)");
                try {
                    CursorId cursorId = dml().openCursor(session, request);
                    timePoints.mark("SCAN: cursorID opened");
                    dml().closeCursor(session, cursorId);
                } catch (NoSuchIndexException e) {
                    timePoints.mark("SCAN: NoSuchIndexException");
                }
            }

            @Override
            protected void handleCaught(TimePoints timePoints, Session session, Throwable t) {
                timePoints.mark("SCAN: Unexpected exception " + t.getClass().getSimpleName());
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<TimedResult<Throwable>> scanFuture = executor.submit(scanCallable);
        Future<TimedResult<Throwable>> dropIndexFuture = executor.submit(dropIndexCallable);

        TimedResult<Throwable> scanResult = scanFuture.get();
        TimedResult<Throwable> dropIndexResult = dropIndexFuture.get();

        new TimePointsComparison(scanResult, dropIndexResult).verify(
                "SCAN: PREPARING",
                "(SCAN: PAUSE)>",
                "DROP: PREPARING",
                "DROP: IN",
                "<(SCAN: PAUSE)",
                "DROP: OUT",
                "SCAN: NoSuchIndexException"
        );

        TimedExceptionCatcher.throwIfThrown(scanResult);
        TimedExceptionCatcher.throwIfThrown(dropIndexResult);
    }
}
