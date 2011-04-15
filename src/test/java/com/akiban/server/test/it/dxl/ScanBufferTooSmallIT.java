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
import com.akiban.ais.model.Table;
import com.akiban.ais.model.UserTable;
import com.akiban.server.InvalidOperationException;
import com.akiban.server.api.FixedCountLimit;
import com.akiban.server.api.HapiGetRequest;
import com.akiban.server.api.HapiRequestException;
import com.akiban.server.api.dml.scan.BufferFullException;
import com.akiban.server.api.dml.scan.CursorId;
import com.akiban.server.api.dml.scan.LegacyRowOutput;
import com.akiban.server.api.dml.scan.RowDataOutput;
import com.akiban.server.api.dml.scan.ScanAllRequest;
import com.akiban.server.api.dml.scan.ScanFlag;
import com.akiban.server.api.dml.scan.ScanRequest;
import com.akiban.server.api.dml.scan.WrappingRowOutput;
import com.akiban.server.api.hapi.DefaultHapiGetRequest;
import com.akiban.server.service.memcache.hprocessor.Scanrows;
import com.akiban.server.service.memcache.outputter.DummyOutputter;
import com.akiban.server.test.it.ITBase;
import org.junit.Before;
import org.junit.Test;
import sun.plugin2.gluegen.runtime.BufferFactory;

import javax.naming.LimitExceededException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public final class ScanBufferTooSmallIT extends ITBase {

    @Before
    public void createTables() throws InvalidOperationException {
        int cid = createTable("ts", "c",
                "cid int key",
                "name varchar(255)");
        int oid = createTable("ts", "o",
                "oid int key",
                "cid int",
                "CONSTRAINT __akiban_fk_c FOREIGN KEY __akiban_fk_c (cid) REFERENCES c(cid)");
        int iid = createTable("ts", "i",
                "iid int key",
                "oid int",
                "CONSTRAINT __akiban_fk_o FOREIGN KEY __akiban_fk_o (oid) REFERENCES o(oid)");

        writeRows(
                createNewRow(cid, 1, "short name"),
                createNewRow(oid, 1, 1),
                createNewRow(iid, 1, 1),
                createNewRow(iid, 2, 1),

                createNewRow(cid, 2, "this name is much longer than the previous name, which was short")
        );
    }

    @Test(expected=BufferFullException.class)
    public void onUserTable() throws InvalidOperationException, BufferFullException {
        UserTable userTable = getUserTable("ts", "c");
        doTest(userTable, userTable.getPrimaryKey().getIndex().getIndexId());
    }

    @Test(expected=BufferFullException.class)
    public void onGroupTable() throws InvalidOperationException, BufferFullException {
        UserTable userTable = getUserTable("ts", "c");
        Table groupTable = userTable.getGroup().getGroupTable();

        int uTablePKID = userTable.getPrimaryKey().getIndex().getIndexId();
        Index uTablePKOnGroup = null;
        for (Index groupTableIndex : groupTable.getIndexes()) {
            if (groupTableIndex.getIndexId() == uTablePKID) {
                uTablePKOnGroup = groupTableIndex;
                break;
            }
        }
        if (uTablePKOnGroup == null) {
            throw new NullPointerException();
        }

        doTest(groupTable, uTablePKOnGroup.getIndexId());
    }

    private void doTest(Table table, int indexId) throws InvalidOperationException, BufferFullException {
        Set<Integer> columns = allColumns(table);
        int size = sizeForOneRow(table.getTableId(), indexId, columns);
        LegacyRowOutput tooSmallOutput = new WrappingRowOutput( ByteBuffer.allocate(size) );
        tooSmallOutput.getOutputBuffer().mark();

        ScanRequest request = new ScanAllRequest(
                table.getTableId(), columns, indexId,
                EnumSet.of(ScanFlag.START_AT_BEGINNING, ScanFlag.END_AT_END)
        );
        CursorId cursorId = dml().openCursor(session(), aisGeneration(), request);
        try {
            dml().scanSome(session(), cursorId, tooSmallOutput);
        } finally {
            dml().closeCursor(session(), cursorId);
        }
    }

    private Set<Integer> allColumns(Table table) {
        Set<Integer> cols = new HashSet<Integer>();
        int colsCount = table.getColumns().size();
        while (--colsCount >= 0) {
            cols.add(colsCount);
        }
        return cols;
    }

    private int sizeForOneRow(int tableId, int indexId, Set<Integer> columns) throws InvalidOperationException {
        LegacyRowOutput output = new WrappingRowOutput( ByteBuffer.allocate(1024) ); // plenty of space!
        output.getOutputBuffer().mark();

        ScanRequest request = new ScanAllRequest(
                tableId, columns, indexId,
                EnumSet.of(ScanFlag.START_AT_BEGINNING, ScanFlag.END_AT_END),
                new FixedCountLimit(1)
        );
        CursorId cursorId = dml().openCursor(session(), aisGeneration(), request);
        try {
            dml().scanSome(session(), cursorId, output);
        } catch (BufferFullException e) {
            throw new RuntimeException(e);
        } finally {
            dml().closeCursor(session(), cursorId);
        }

        return output.getOutputBuffer().position();
    }
}
