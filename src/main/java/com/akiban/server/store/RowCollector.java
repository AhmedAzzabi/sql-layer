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

package com.akiban.server.store;

import com.akiban.server.IndexDef;
import com.akiban.server.RowData;
import com.akiban.server.api.dml.scan.ScanLimit;

import java.nio.ByteBuffer;

public interface RowCollector {

    public final int SCAN_FLAGS_DESCENDING = 1 << 0;

    public final int SCAN_FLAGS_START_EXCLUSIVE = 1 << 1;

    public final int SCAN_FLAGS_END_EXCLUSIVE = 1 << 2;

    public final int SCAN_FLAGS_SINGLE_ROW = 1 << 3;

    public final int SCAN_FLAGS_PREFIX = 1 << 4;

    public final int SCAN_FLAGS_START_AT_EDGE = 1 << 5;

    public final int SCAN_FLAGS_END_AT_EDGE = 1 << 6;

    public final int SCAN_FLAGS_DEEP = 1 << 7;

    /**
     * Place the next row into payload if there is another row, and if there is room in payload.
     * @param payload
     * @return true if a row was placed into payload, false otherwise
     * @throws Exception
     */
    public boolean collectNextRow(ByteBuffer payload) throws Exception;

    public RowData collectNextRow() throws Exception;

    public boolean hasMore();

    public void open();

    public void close();
    
    public int getDeliveredRows();

    public int getDeliveredBuffers();
    
    public long getDeliveredBytes();
    
    public int getTableId();

    public IndexDef getIndexDef();

    public long getId();

    public void outputToMessage(boolean outputToMessage);

    public boolean checksLimit();
}
