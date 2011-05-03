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

package com.akiban.server.test.mt.mtatomics;

import com.akiban.ais.model.TableName;
import com.akiban.server.InvalidOperationException;
import com.akiban.server.api.DMLFunctions;
import com.akiban.server.api.FixedCountLimit;
import com.akiban.server.api.dml.SetColumnSelector;
import com.akiban.server.api.dml.NoSuchIndexException;
import com.akiban.server.api.dml.scan.BufferFullException;
import com.akiban.server.api.dml.scan.BufferedLegacyOutputRouter;
import com.akiban.server.api.dml.scan.ConcurrentScanAndUpdateException;
import com.akiban.server.api.dml.scan.CursorId;
import com.akiban.server.api.dml.scan.LegacyOutputConverter;
import com.akiban.server.api.dml.scan.LegacyRowOutput;
import com.akiban.server.api.dml.scan.NewRow;
import com.akiban.server.api.dml.scan.NiceRow;
import com.akiban.server.api.dml.scan.ScanAllRequest;
import com.akiban.server.api.dml.scan.ScanFlag;
import com.akiban.server.api.dml.scan.ScanLimit;
import com.akiban.server.api.dml.scan.WrappingRowOutput;
import com.akiban.server.service.ServiceManagerImpl;
import com.akiban.server.test.mt.mtutil.TimePoints;
import com.akiban.server.test.mt.mtutil.TimePointsComparison;
import com.akiban.server.test.mt.mtutil.TimedCallable;
import com.akiban.server.test.mt.mtutil.TimedResult;
import com.akiban.server.test.mt.mtutil.Timing;
import com.akiban.server.service.session.Session;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class ConcurrentDMLAtomicsMT extends ConcurrentAtomicsBase {
    @Test
    public void updateUnIndexedColumnWhileScanning() throws Exception {
        final int tableId = tableWithThreeRows();
        final int SCAN_WAIT = 5000;

        int indexId = ddl().getUserTable(session(), new TableName(SCHEMA, TABLE)).getIndex("PRIMARY").getIndexId();
        TimedCallable<List<NewRow>> scanCallable
                = new DelayScanCallableBuilder(aisGeneration(), tableId, indexId)
                .topOfLoopDelayer(1, SCAN_WAIT, "SCAN: PAUSE").get();
        TimedCallable<Void> updateCallable = new TimedCallable<Void>() {
            @Override
            protected Void doCall(TimePoints timePoints, Session session) throws Exception {
                NewRow old = createNewRow(tableId, 2L, "mr melty");
                NewRow updated = createNewRow(tableId, 2L, "icebox");
                Timing.sleep(2000);
                timePoints.mark("UPDATE: IN");
                dml().updateRow(ServiceManagerImpl.newSession(), old, updated, new SetColumnSelector(1));
                timePoints.mark("UPDATE: OUT");
                return null;
            }
        };

        scanUpdateConfirm(
                tableId,
                scanCallable,
                updateCallable,
                Arrays.asList(
                        createNewRow(tableId, 1L, "the snowman"),
                        createNewRow(tableId, 2L, "icebox"),
                        createNewRow(tableId, 99L, "zebras in snow")
                ),
                Arrays.asList(
                        createNewRow(tableId, 1L, "the snowman"),
                        createNewRow(tableId, 2L, "icebox"),
                        createNewRow(tableId, 99L, "zebras in snow")
                )
        );
    }

    @Test
    public void updatePKColumnWhileScanning() throws Exception {
        final int tableId = tableWithThreeRows();
        final int SCAN_WAIT = 5000;

        int indexId = ddl().getUserTable(session(), new TableName(SCHEMA, TABLE)).getIndex("PRIMARY").getIndexId();
        TimedCallable<List<NewRow>> scanCallable
                = new DelayScanCallableBuilder(aisGeneration(), tableId, indexId)
                .topOfLoopDelayer(1, SCAN_WAIT, "SCAN: PAUSE").get();
        TimedCallable<Void> updateCallable = new TimedCallable<Void>() {
            @Override
            protected Void doCall(TimePoints timePoints, Session session) throws Exception {
                NewRow old = createNewRow(tableId, 1L, "the snowman");
                NewRow updated = createNewRow(tableId, 5L);
                Timing.sleep(2000);
                timePoints.mark("UPDATE: IN");
                dml().updateRow(ServiceManagerImpl.newSession(), old, updated, new SetColumnSelector(0));
                timePoints.mark("UPDATE: OUT");
                return null;
            }
        };

        scanUpdateConfirm(
                tableId,
                scanCallable,
                updateCallable,
                Arrays.asList(
                        createNewRow(tableId, 2L, "mr melty"),
                        createNewRow(tableId, 5L, "the snowman"),
                        createNewRow(tableId, 99L, "zebras in snow")
                ),
                Arrays.asList(
                        createNewRow(tableId, 2L, "mr melty"),
                        createNewRow(tableId, 5L, "the snowman"),
                        createNewRow(tableId, 99L, "zebras in snow")
                )
        );
    }

    @Test
    public void updateIndexedColumnWhileScanning() throws Exception {
        final int tableId = tableWithThreeRows();
        final int SCAN_WAIT = 5000;

        int indexId = ddl().getUserTable(session(), new TableName(SCHEMA, TABLE)).getIndex("name").getIndexId();
        TimedCallable<List<NewRow>> scanCallable
                = new DelayScanCallableBuilder(aisGeneration(), tableId, indexId)
                .topOfLoopDelayer(1, SCAN_WAIT, "SCAN: PAUSE").get();
//        scanCallable = TransactionalTimedCallable.withRunnable( scanCallable, 10, 1000 );
//        scanCallable = TransactionalTimedCallable.withoutRunnable(scanCallable);

        TimedCallable<Void> updateCallable = new TimedCallable<Void>() {
            @Override
            protected Void doCall(TimePoints timePoints, Session session) throws Exception {
                NewRow oldSnowman = createNewRow(tableId, 1L, "the snowman");
                NewRow updatedSnowman = createNewRow(tableId, UNDEF, "a snowman");
                NewRow oldMr = createNewRow(tableId, 2L, "mr melty");
                NewRow updatedMr = createNewRow(tableId, UNDEF, "xtreme weather");

                Timing.sleep(2000);
                timePoints.mark("UPDATE: IN");
                dml().updateRow(ServiceManagerImpl.newSession(), oldSnowman, updatedSnowman, new SetColumnSelector(1));
                dml().updateRow(ServiceManagerImpl.newSession(), oldMr, updatedMr, new SetColumnSelector(1));
                timePoints.mark("UPDATE: OUT");
                return null;
            }
        };

        scanUpdateConfirm(
                tableId,
                scanCallable,
                updateCallable,
                Arrays.asList(
                        createNewRow(tableId, 1L, "a snowman"),
                        createNewRow(tableId, 2L, "xtreme weather"),
                        createNewRow(tableId, 99L, "zebras in snow")
                ),
                Arrays.asList(
                        createNewRow(tableId, 1L, "a snowman"),
                        createNewRow(tableId, 2L, "xtreme weather"),
                        createNewRow(tableId, 99L, "zebras in snow")
                )
        );
    }

    @Test
    public void updateIndexedColumnAndPKWhileScanning() throws Exception {
        final int tableId = tableWithThreeRows();
        final int SCAN_WAIT = 5000;

        int indexId = ddl().getUserTable(session(), new TableName(SCHEMA, TABLE)).getIndex("name").getIndexId();
        TimedCallable<List<NewRow>> scanCallable
                = new DelayScanCallableBuilder(aisGeneration(), tableId, indexId)
                .topOfLoopDelayer(1, SCAN_WAIT, "SCAN: PAUSE").get();
//        scanCallable = TransactionalTimedCallable.withRunnable( scanCallable, 10, 1000 );
//        scanCallable = TransactionalTimedCallable.withoutRunnable(scanCallable);

        TimedCallable<Void> updateCallable = new TimedCallable<Void>() {
            @Override
            protected Void doCall(TimePoints timePoints, Session session) throws Exception {
                NewRow oldSnowman = createNewRow(tableId, 1L, "the snowman");
                NewRow updatedSnowman = createNewRow(tableId, 10L, "a snowman");
                NewRow oldMr = createNewRow(tableId, 2L, "mr melty");
                NewRow updatedMr = createNewRow(tableId, 2L, "xtreme weather");

                Timing.sleep(2000);
                timePoints.mark("UPDATE: IN");
                dml().updateRow(ServiceManagerImpl.newSession(), oldSnowman, updatedSnowman, new SetColumnSelector(0, 1));
                dml().updateRow(ServiceManagerImpl.newSession(), oldMr, updatedMr, new SetColumnSelector(1));
                timePoints.mark("UPDATE: OUT");
                return null;
            }
        };

        scanUpdateConfirm(
                tableId,
                scanCallable,
                updateCallable,
                Arrays.asList(
                        createNewRow(tableId, 10L, "a snowman"),
                        createNewRow(tableId, 2L, "xtreme weather"),
                        createNewRow(tableId, 99L, "zebras in snow")
                ),
                Arrays.asList(
                        createNewRow(tableId, 2L, "xtreme weather"),
                        createNewRow(tableId, 10L, "a snowman"),
                        createNewRow(tableId, 99L, "zebras in snow")
                )
        );
    }

    @Ignore("bug 746681")
    @Test(expected=ConcurrentScanAndUpdateException.class)
    public void multipleScanSomeCalls() throws Throwable {
        final int SCAN_WAIT = 5000;
        final int tableId = tableWithTwoRows();
        final int pkId = ddl().getUserTable(session(), new TableName(SCHEMA, TABLE))
                .getPrimaryKey().getIndex().getIndexId();
        final int size = findOneRowBufferSize(tableId, pkId);

        TimedCallable<List<NewRow>> scanCallable = new MultiScanSomeCallable(size,
                tableId, pkId,
                SCAN_WAIT,
                dml(), aisGeneration()
        );
        TimedCallable<Void> updateCallable = new TimedCallable<Void>() {
            @Override
            protected Void doCall(TimePoints timePoints, Session session) throws Exception {
                NewRow oldSnowman = createNewRow(tableId, 1L, "the snowman");
                NewRow updatedSnowman = createNewRow(tableId, 10L, "a snowman");
                NewRow oldMr = createNewRow(tableId, 2L, "mr melty");
                NewRow updatedMr = createNewRow(tableId, 2L, "xtreme weather");

                Timing.sleep(2000);
                timePoints.mark("UPDATE: IN");
                dml().updateRow(ServiceManagerImpl.newSession(), oldSnowman, updatedSnowman, new SetColumnSelector(0, 1));
                dml().updateRow(ServiceManagerImpl.newSession(), oldMr, updatedMr, new SetColumnSelector(1));
                timePoints.mark("UPDATE: OUT");
                return null;
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<TimedResult<List<NewRow>>> scanFuture = executor.submit(scanCallable);
        Future<TimedResult<Void>> updateFuture = executor.submit(updateCallable);

        TimedResult<Void> updateResult =updateFuture.get();
        Throwable scanFutureException = null;
        try {
            TimedResult<List<NewRow>> result = scanFuture.get();
            TimePointsComparison comparison = new TimePointsComparison(updateResult, result);
            fail( String.format("%s => %s", comparison, result.getItem()) );
        } catch (ExecutionException e) {
            scanFutureException = e.getCause();
        }

        expectFullRows(
                tableId,
                createNewRow(tableId, 2L, "xtreme weather"),
                createNewRow(tableId, 10L, "a snowman")
        );

        if (scanFutureException != null) {
            throw scanFutureException;
        }
    }

    private int findOneRowBufferSize(int tableId, int indexId) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(1024); // should be plenty
        buffer.mark();
        LegacyRowOutput output = new WrappingRowOutput(buffer);

        ScanAllRequest request = new ScanAllRequest(
                tableId,
                new HashSet<Integer>(Arrays.asList(0, 1)),
                indexId,
                EnumSet.of(ScanFlag.START_AT_BEGINNING, ScanFlag.END_AT_END),
                new FixedCountLimit(1)
        );
        CursorId cursorId = dml().openCursor(session(), aisGeneration(), request);
        dml().scanSome(session(), cursorId, output);
        dml().closeCursor(session(), cursorId);
        assertFalse("buffer pos still 0", buffer.position() == 0);
        return buffer.position();
    }

    /**
     * This class duplicates some of DelayScanCallable, but it adds enough that I felt it would over-complicate
     * DelayableScanCallable too much to put that logic there.
     */
    private static class MultiScanSomeCallable extends TimedCallable<List<NewRow>> {
        private final int tableId;
        private final int indexId;
        private final long delayTime;
        private final int size;
        private final int aisGeneration;
        private final DMLFunctions dml;

        private MultiScanSomeCallable(int size, int tableId, int indexId, long delayTime, DMLFunctions dml,
                                      int aisGeneration)
        {
            this.tableId = tableId;
            this.indexId = indexId;
            this.delayTime = delayTime;
            this.size = size;
            this.dml = dml;
            this.aisGeneration = aisGeneration;
        }

        @Override
        protected List<NewRow> doCall(TimePoints timePoints, Session session) throws Exception {
            BufferedLegacyOutputRouter smallRouter = new BufferedLegacyOutputRouter(size, false);
            LegacyOutputConverter converter = new LegacyOutputConverter(dml);
            ListRowOutput output = new ListRowOutput();
            converter.setOutput(output);
            converter.setColumnsToScan(new HashSet<Integer>(Arrays.asList(0, 1)));
            smallRouter.addHandler(converter);


            ScanAllRequest request = new ScanAllRequest(
                    tableId,
                    new HashSet<Integer>(Arrays.asList(0, 1)),
                    indexId,
                    EnumSet.of(ScanFlag.START_AT_BEGINNING, ScanFlag.END_AT_END),
                    ScanLimit.NONE
            );

            final CursorId cursorId;
            try {
                cursorId = dml.openCursor(session, aisGeneration, request);
            } catch (NoSuchIndexException e) {
                timePoints.mark("SCAN: NO SUCH INDEX");
                return Collections.emptyList();
            }

            timePoints.mark("SCAN: START");
            boolean bufferFilled = false;
            try {
                dml.scanSome(session, cursorId, smallRouter);
            } catch (BufferFullException e) {
                timePoints.mark("SCAN: FIRST BUFFER FULL");
                bufferFilled = true;
            }
            assertTrue("should have had more!", bufferFilled);
            assertEquals("rows scanned", 1, output.getRows().size());
            NewRow row = output.getRows().get(0);
            assertTrue("row isn't NiceRow: " + row.getClass(), row instanceof NiceRow);

            timePoints.mark("(SCAN: PAUSE)>");
            Timing.sleep(delayTime);
            timePoints.mark("<(SCAN: PAUSE)");

            BufferedLegacyOutputRouter bigRouter = new BufferedLegacyOutputRouter(1024, false);
            bigRouter.addHandler(converter);
            timePoints.mark("SCAN: SECOND (should fail)");
            dml.scanSome(session, cursorId, bigRouter);

            dml.closeCursor(session, cursorId);
            timePoints.mark("SCAN: FINISH");

            return output.getRows();
        }
    }

    protected int tableWithThreeRows() throws InvalidOperationException {
        int tableId = tableWithTwoRows();
        writeRows(
                createNewRow(tableId, 99L, "zebras in snow")
        );
        return tableId;
    }
}
