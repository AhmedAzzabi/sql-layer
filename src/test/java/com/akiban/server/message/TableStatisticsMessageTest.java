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

package com.akiban.server.message;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;

import org.junit.Test;

import com.akiban.server.RowData;
import com.akiban.server.RowDef;
import com.akiban.server.RowDefCache;
import com.akiban.server.SchemaFactory;
import com.akiban.server.TableStatistics;
import com.persistit.Key;
import com.persistit.Persistit;

public class TableStatisticsMessageTest {

    @Test
    public void testTableStatisticsResponseMessageSerialization()
            throws Exception {
        String[] DDL = new String[] {
            "use schema; ",
            "create table test(",
            "    a tinyint, ",
            "    b tinyint, ",
            "    c smallint, ",
            "    unique index(b));"
        };
        SchemaFactory schemaFactory = new SchemaFactory();
        RowDefCache rowDefCache = schemaFactory.rowDefCache(DDL);
        RowDef rowDef = rowDefCache.getRowDef("schema", "test");
        final TableStatistics ts = new TableStatistics(123);
        ts.setAutoIncrementValue(999);
        ts.setBlockSize(8192);
        ts.setCreationTime(88888);
        ts.setMeanRecordLength(200);
        ts.setRowCount(12345678);
        ts.setUpdateTime(99999);
        final TableStatistics.Histogram hs = new TableStatistics.Histogram(456);
        final Key key = new Key((Persistit) null);
        for (int i = 1; i <= 10; i++) {
            final RowData rowData = new RowData(new byte[256]);
            rowData.createRow(rowDef, new Object[] { null, i, null });
            final TableStatistics.HistogramSample sample = new TableStatistics.HistogramSample(
                    rowData, i * 7);
            hs.addSample(sample);
        }
        ts.addHistogram(hs);

        final ByteBuffer payload = ByteBuffer.allocate(65536);
        final GetTableStatisticsResponse message = new GetTableStatisticsResponse(
                123, ts);

        message.write(payload);
        payload.flip();

        final GetTableStatisticsResponse message2 = new GetTableStatisticsResponse();
        message2.read(payload);
        assertEquals(999, ts.getAutoIncrementValue());
        assertEquals(8192, ts.getBlockSize());
        assertEquals(88888, ts.getCreationTime());
        assertEquals(200, ts.getMeanRecordLength());
        assertEquals(12345678, ts.getRowCount());
        assertEquals(99999, ts.getUpdateTime());
        assertEquals(123, ts.getRowDefId());
        assertEquals(1, ts.getHistogramList().size());
        assertEquals(10, ts.getHistogramList().get(0).getHistogramSamples()
                .size());
        assertEquals(payload.position(), payload.limit());
    }
}
