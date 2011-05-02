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

package com.akiban.server.service.dxl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.akiban.server.IndexDef;
import com.akiban.server.RowData;
import com.akiban.server.api.FixedCountLimit;
import com.akiban.server.api.dml.scan.BufferFullException;
import com.akiban.server.api.dml.scan.ScanLimit;
import com.persistit.exception.PersistitException;
import org.junit.Assert;
import org.junit.Test;

import com.akiban.server.InvalidOperationException;
import com.akiban.server.api.dml.scan.Cursor;
import com.akiban.server.api.dml.scan.CursorId;
import com.akiban.server.api.dml.scan.CursorIsFinishedException;
import com.akiban.server.api.dml.scan.LegacyRowOutput;
import com.akiban.server.store.RowCollector;

public final class BasicDMLFunctionsTest {
    
    private static class DummyScanner extends BasicDMLFunctions.Scanner {
        @Override
        protected RowData getRowData(byte[] bytes, int offset, int length) {
            return null;
        }
    }
    
    private final BasicDMLFunctions.Scanner scanner = new DummyScanner();
    
    private static class StringRowCollector implements RowCollector {
        private final List<String> strings;
        private final int tableId;
        private boolean open;
        private int deliveredRows;

        public StringRowCollector(int tableId, String... strings) {
            this.tableId = tableId;
            this.strings = new ArrayList<String>(Arrays.asList(strings));
            open = true;
        }

        @Override
        public boolean collectNextRow(ByteBuffer payload) {
            checkOpen();
            if (strings.isEmpty()) {
                return false;
            }
            String string = strings.remove(0);
            payload.putInt(string.length());
            payload.put(string.getBytes());
            ++deliveredRows;

            return true;
        }

        @Override
        public RowData collectNextRow() throws Exception
        {
            Assert.fail();
            return null;
        }

        @Override
        public boolean hasMore() throws Exception {
            checkOpen();
            return ! strings.isEmpty();
        }

        private void checkOpen() {
            if (!open) {
                throw new IllegalStateException("not open");
            }
        }

        @Override
        public void close() {
            checkOpen();
            open = false;
        }

        @Override
        public int getDeliveredRows() {
            return deliveredRows;
        }

        @Override
        public int getDeliveredBuffers() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getRepeatedRows() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getDeliveredBytes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getTableId() {
            return tableId;
        }

        @Override
        public IndexDef getIndexDef() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void outputToMessage(boolean outputToMessage)
        {
            assert outputToMessage;
        }
    }

    private static class StringRowOutput implements LegacyRowOutput {
        private final ByteBuffer buffer;
        
        private StringRowOutput() {
            buffer = ByteBuffer.allocate(2048); // should be plenty
            buffer.putInt(0);
        }

        @Override
        public ByteBuffer getOutputBuffer() {
            return buffer;
        }

        public List<String> getStrings() {
            int rowsLeft = getRowsCount();
            final List<String> ret = new ArrayList<String>(rowsLeft);
            int pos = Integer.SIZE / 8; // first int is the number of rows
            while ( (rowsLeft--) > 0) {
                int length = buffer.getInt(pos);
                pos += Integer.SIZE / 8;
                byte[] bytes = new byte[length];
                for (int off=0; off < length; ++off) {
                    bytes[off] = buffer.get(pos + off);
                }
                pos += length;
                ret.add(new String(bytes));
            }
            return ret;
        }

        @Override
        public void wroteRow(boolean limitExceeded) {
            if (!limitExceeded) {
                buffer.putInt(0, 1 + getRowsCount() );
            }
        }

        @Override
        public void addRow(RowData rowData)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getRowsCount() {
            return buffer.getInt(0);
        }

        @Override
        public boolean getOutputToMessage()
        {
            return true;
        }

        @Override
        public void mark() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void rewind() {
            throw new UnsupportedOperationException();
        }
    }

    private static class TestingStruct {
        final String[] stringsArray;
        final StringRowCollector collector;
        final StringRowOutput output;
        final Cursor cursor;
        final CursorId cursorId;

        TestingStruct(ScanLimit limit, String... strings) {
            final int TABLE_ID = 3;
            stringsArray = strings;
            collector = new StringRowCollector(TABLE_ID, strings);
            output = new StringRowOutput();
            cursor = new Cursor(collector, limit, null);
            cursorId = new CursorId(5, TABLE_ID);
        }
    }

    @Test(expected= CursorIsFinishedException.class)
    public void scansNoLimit() throws InvalidOperationException, BufferFullException, PersistitException {
        final TestingStruct s = new TestingStruct(ScanLimit.NONE, "Hi", "there", "pooh bear", "how are you there");

        try {
            assertFalse("expected end", scanner.doScan(s.cursor, s.cursorId, s.output, BasicDMLFunctions.DEFAULT_SCAN_HOOK));

            assertEquals("rc rows delivered", s.stringsArray.length, s.collector.getDeliveredRows());
            assertEquals("output rows written", s.stringsArray.length, s.output.getRowsCount());

            assertEquals("rows seen", Arrays.asList(s.stringsArray), s.output.getStrings());
        } catch (InvalidOperationException e) {
            throw new RuntimeException(e);
        }

        scanner.doScan(s.cursor, s.cursorId, s.output, BasicDMLFunctions.DEFAULT_SCAN_HOOK);
    }

    @Test(expected= CursorIsFinishedException.class)
    public void scansWithLimit() throws InvalidOperationException, BufferFullException, PersistitException {
        final TestingStruct s = new TestingStruct(new FixedCountLimit(1), "hi", "world", "and", "universe");

        try {
            assertFalse("expected no more", scanner.doScan(s.cursor, s.cursorId, s.output, BasicDMLFunctions.DEFAULT_SCAN_HOOK));
            assertEquals("rc rows delivered", 2, s.collector.getDeliveredRows());
            assertEquals("output rows written", 1, s.output.getRowsCount());
            assertEquals("rows seen", Arrays.asList("hi"), s.output.getStrings());
        } catch (InvalidOperationException e) {
            throw new RuntimeException(e);
        }

        scanner.doScan(s.cursor, s.cursorId, s.output, BasicDMLFunctions.DEFAULT_SCAN_HOOK);
    }

    @Test(expected= CursorIsFinishedException.class)
    public void scanEmptyRC() throws InvalidOperationException, BufferFullException, PersistitException {
        final TestingStruct s = new TestingStruct(new FixedCountLimit(0));
        try {
            assertFalse("expected end", scanner.doScan(s.cursor, s.cursorId, s.output, BasicDMLFunctions.DEFAULT_SCAN_HOOK));
        } catch (InvalidOperationException e) {
            throw new RuntimeException(e);
        }

        scanner.doScan(s.cursor, s.cursorId, s.output, BasicDMLFunctions.DEFAULT_SCAN_HOOK);
    }
}
