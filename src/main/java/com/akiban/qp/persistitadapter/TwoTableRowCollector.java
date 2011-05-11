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

package com.akiban.qp.persistitadapter;

import com.akiban.ais.model.Column;
import com.akiban.ais.model.GroupTable;
import com.akiban.ais.model.Index;
import com.akiban.ais.model.UserTable;
import com.akiban.qp.expression.IndexBound;
import com.akiban.qp.expression.IndexKeyRange;
import com.akiban.qp.rowtype.IndexKeyType;
import com.akiban.server.RowData;
import com.akiban.server.RowDef;
import com.akiban.server.api.dml.ByteArrayColumnSelector;
import com.akiban.server.api.dml.ColumnSelector;
import com.akiban.server.api.dml.scan.NiceRow;
import com.akiban.server.service.session.Session;
import com.akiban.server.store.PersistitStore;

public class TwoTableRowCollector extends OperatorBasedRowCollector
{
    TwoTableRowCollector(Session session,
                         PersistitStore store,
                         RowDef rowDef,
                         int indexId,
                         int scanFlags,
                         RowData start,
                         ColumnSelector startColumns,
                         RowData end,
                         ColumnSelector endColumns,
                         byte[] columnBitMap,
                         OpenInfoStruct openInfoStruct)
    {
        super(store, session, openInfoStruct);
        // Get group table and check that start/end refer to it
        GroupTable groupTable = rowDef.groupTable();
        assert start == null || start.getRowDefId() == groupTable.getTableId() : start;
        assert end == null || end.getRowDefId() == groupTable.getTableId() : end;
        // Analyses based on group columns
        NiceRow userTableStart = null;
        NiceRow userTableEnd = null;
        UserTable predicateTable = null;
        int columnPosition = 0;
        int columnBitMapSize = columnBitMap.length * 8;
        while (columnPosition < columnBitMapSize) {
            if ((columnBitMap[columnPosition / 8] & (1 << (columnPosition % 8))) != 0) {
                Column groupColumn = groupTable.getColumnsIncludingInternal().get(columnPosition);
                Column userColumn = groupColumn.getUserColumn();
                UserTable userTable = userColumn.getUserTable();
                requiredUserTables.add(userTable);
                // rootmostQueryTable
                if (queryRootTable == null) {
                    queryRootTable = userTable;
                }
                // predicateTable, predicateRowDef, start/end translated to UserTable rows.
                if (start != null && startColumns.includesColumn(columnPosition) ||
                    end != null && endColumns.includesColumn(columnPosition)) {
                    if (predicateTable == null) {
                        predicateTable = userTable;
                        userTableStart = start == null ? null : new NiceRow(predicateTable.getTableId());
                        userTableEnd = end == null ? null : new NiceRow(predicateTable.getTableId());
                    } else if (predicateTable != userTable) {
                        throw new IllegalArgumentException
                            (String.format("Restriction on at least two tables: %s, %s",
                                           predicateTable, userTable));
                    }
                    if (userTableStart != null) {
                        userTableStart.put(userColumn.getPosition(),
                                           start.toObject(rowDef, groupColumn.getPosition()));
                    }
                    if (userTableEnd != null) {
                        userTableEnd.put(userColumn.getPosition(),
                                         end.toObject(rowDef, groupColumn.getPosition()));
                    }
                }
            }
            columnPosition++;
        }
        assert queryRootTable != null;
        queryRootType = schema.userTableRowType(queryRootTable);
        if (predicateTable != null) {
            // predicateIndex and predicateType
            predicateIndex = null;
            for (Index userTableIndex : predicateTable.getIndexesIncludingInternal()) {
                if (userTableIndex.getIndexId() == indexId) {
                    predicateIndex = userTableIndex;
                }
            }
            assert predicateIndex != null : String.format("table %s, index %s", predicateTable, indexId);
            predicateType = schema.userTableRowType((UserTable) predicateIndex.getTable());
            // Index bounds
            IndexBound lo =
                userTableStart == null
                ? null
                : new IndexBound(predicateTable,
                                 PersistitGroupRow.newPersistitGroupRow(adapter, userTableStart.toRowData()),
                                 userColumnSelector(predicateTable, startColumns));
            IndexBound hi =
                userTableEnd == null
                ? null
                : new IndexBound(predicateTable,
                                 PersistitGroupRow.newPersistitGroupRow(adapter, userTableEnd.toRowData()),
                                 userColumnSelector(predicateTable, endColumns));
            indexKeyRange = new IndexKeyRange
                (lo,
                 lo != null && (scanFlags & (SCAN_FLAGS_START_AT_EDGE | SCAN_FLAGS_START_EXCLUSIVE)) == 0,
                 hi,
                 hi != null && (scanFlags & (SCAN_FLAGS_END_AT_EDGE | SCAN_FLAGS_END_EXCLUSIVE)) == 0);
        }
    }

    private ColumnSelector userColumnSelector(UserTable table, ColumnSelector groupColumnSelector)
    {
        int nColumns = table.getColumnsIncludingInternal().size();
        byte[] columnBitMap = new byte[(nColumns + 7) / 8];
        for (Column userColumn : table.getColumnsIncludingInternal()) {
            Column groupColumn = userColumn.getGroupColumn();
            if (groupColumnSelector.includesColumn(groupColumn.getPosition())) {
                int p = userColumn.getPosition();
                columnBitMap[p / 8] |= 1 << (p % 8);
            }
        }
        return new ByteArrayColumnSelector(columnBitMap);
    }
}
